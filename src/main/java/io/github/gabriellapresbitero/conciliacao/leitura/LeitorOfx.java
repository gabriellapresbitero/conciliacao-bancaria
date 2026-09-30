package io.github.gabriellapresbitero.conciliacao.leitura;

import io.github.gabriellapresbitero.conciliacao.modelo.Lancamento;
import io.github.gabriellapresbitero.conciliacao.modelo.Origem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê extratos no formato OFX, que quase todo banco brasileiro exporta
 * (no internet banking, costuma aparecer como "Money" ou "OFX").
 *
 * <p>Cada movimentação fica num bloco {@code <STMTTRN>}:
 * <pre>
 * &lt;STMTTRN&gt;
 *   &lt;TRNTYPE&gt;DEBIT
 *   &lt;DTPOSTED&gt;20250310120000[-3:BRT]
 *   &lt;TRNAMT&gt;-150.00
 *   &lt;FITID&gt;202503100001
 *   &lt;MEMO&gt;PIX ENVIADO FORNECEDOR
 * &lt;/STMTTRN&gt;
 * </pre>
 * No OFX 1.x (o mais comum) as tags de valor não são fechadas,
 * por isso a leitura é feita com expressões regulares e não com um leitor XML.
 */
public class LeitorOfx {

    private static final Pattern BLOCO = Pattern.compile("<STMTTRN>(.*?)</STMTTRN>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    public List<Lancamento> ler(Path arquivo) throws IOException {
        byte[] bytes = Files.readAllBytes(arquivo);
        // Bancos brasileiros costumam gerar OFX em Windows-1252 (acentos "latinos").
        String conteudo = new String(bytes, StandardCharsets.ISO_8859_1);
        if (conteudo.toUpperCase().matches("(?s).*(ENCODING:UTF-8|ENCODING=\"UTF-8\").*")) {
            conteudo = new String(bytes, StandardCharsets.UTF_8);
        }
        return ler(conteudo, arquivo.getFileName().toString());
    }

    public List<Lancamento> ler(String conteudo, String nomeArquivo) {
        List<Lancamento> lancamentos = new ArrayList<>();
        Matcher bloco = BLOCO.matcher(conteudo);
        int numero = 0;
        while (bloco.find()) {
            numero++;
            String texto = bloco.group(1);
            try {
                String dataOfx = tag(texto, "DTPOSTED");
                String id = tag(texto, "FITID");
                String memo = tag(texto, "MEMO");
                String descricao = memo.isBlank() ? tag(texto, "NAME") : memo;
                lancamentos.add(new Lancamento(
                        id.isBlank() ? "B-" + numero : id,
                        Conversor.data(dataOfx.length() >= 8 ? dataOfx.substring(0, 8) : dataOfx),
                        descricao,
                        Conversor.valor(tag(texto, "TRNAMT")),
                        Origem.BANCO));
            } catch (IllegalArgumentException e) {
                throw new ArquivoInvalidoException(nomeArquivo + ", transação " + numero + ": " + e.getMessage());
            }
        }
        if (numero == 0) {
            throw new ArquivoInvalidoException(nomeArquivo + ": nenhuma transação <STMTTRN> encontrada");
        }
        return lancamentos;
    }

    /** Pega o valor de uma tag, esteja ela fechada ({@code <MEMO>x</MEMO>}) ou não ({@code <MEMO>x}). */
    private static String tag(String bloco, String nome) {
        Matcher m = Pattern.compile("<" + nome + ">([^<\\r\\n]*)", Pattern.CASE_INSENSITIVE).matcher(bloco);
        return m.find() ? m.group(1).strip() : "";
    }
}
