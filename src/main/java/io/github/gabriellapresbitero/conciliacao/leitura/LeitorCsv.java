package io.github.gabriellapresbitero.conciliacao.leitura;

import io.github.gabriellapresbitero.conciliacao.modelo.Lancamento;
import io.github.gabriellapresbitero.conciliacao.modelo.Origem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lê lançamentos de um CSV separado por ponto e vírgula (o padrão do Excel em português).
 *
 * <p>Colunas obrigatórias: {@code data}, {@code descricao} e {@code valor}.
 * Coluna opcional: {@code documento}, usada como identificador.
 * A ordem das colunas não importa.
 */
public class LeitorCsv {

    public List<Lancamento> ler(Path arquivo, Origem origem) throws IOException {
        String conteudo = Files.readString(arquivo, StandardCharsets.UTF_8);
        return ler(conteudo, origem, arquivo.getFileName().toString());
    }

    public List<Lancamento> ler(String conteudo, Origem origem, String nomeArquivo) {
        String[] linhas = conteudo.replace("﻿", "").split("\\R");
        if (linhas.length == 0 || linhas[0].isBlank()) {
            throw new ArquivoInvalidoException(nomeArquivo + ": arquivo vazio");
        }

        Map<String, Integer> colunas = lerCabecalho(linhas[0], nomeArquivo);
        String prefixo = origem == Origem.BANCO ? "B" : "S";
        List<Lancamento> lancamentos = new ArrayList<>();

        for (int i = 1; i < linhas.length; i++) {
            if (linhas[i].isBlank()) {
                continue;
            }
            int numeroLinha = i + 1;
            List<String> campos = dividir(linhas[i]);
            try {
                String documento = campo(campos, colunas.get("documento"));
                String id = documento.isBlank() ? prefixo + "-" + numeroLinha : documento;
                lancamentos.add(new Lancamento(
                        id,
                        Conversor.data(campo(campos, colunas.get("data"))),
                        campo(campos, colunas.get("descricao")),
                        Conversor.valor(campo(campos, colunas.get("valor"))),
                        origem));
            } catch (IllegalArgumentException e) {
                throw new ArquivoInvalidoException(nomeArquivo, numeroLinha, e.getMessage());
            }
        }
        return lancamentos;
    }

    private Map<String, Integer> lerCabecalho(String linha, String nomeArquivo) {
        Map<String, Integer> colunas = new HashMap<>();
        List<String> nomes = dividir(linha);
        for (int i = 0; i < nomes.size(); i++) {
            // "Descrição" -> "descricao": tira acentos e deixa minúsculo
            String nome = Normalizer.normalize(nomes.get(i).strip(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
            colunas.put(nome.toLowerCase(Locale.ROOT), i);
        }
        for (String obrigatoria : List.of("data", "descricao", "valor")) {
            if (!colunas.containsKey(obrigatoria)) {
                throw new ArquivoInvalidoException(nomeArquivo + ": falta a coluna \"" + obrigatoria
                        + "\" no cabeçalho (colunas encontradas: " + nomes + ")");
            }
        }
        return colunas;
    }

    private static String campo(List<String> campos, Integer posicao) {
        if (posicao == null || posicao >= campos.size()) {
            return "";
        }
        return campos.get(posicao).strip();
    }

    /**
     * Divide uma linha pelo ";", respeitando campos entre aspas.
     * Exemplo: {@code 10/03/2025;"PIX; João";-50,00} vira 3 campos.
     */
    static List<String> dividir(String linha) {
        List<String> campos = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        boolean dentroDeAspas = false;
        for (int i = 0; i < linha.length(); i++) {
            char c = linha.charAt(i);
            if (c == '"') {
                if (dentroDeAspas && i + 1 < linha.length() && linha.charAt(i + 1) == '"') {
                    atual.append('"'); // aspas duplas "" dentro de um campo = uma aspa
                    i++;
                } else {
                    dentroDeAspas = !dentroDeAspas;
                }
            } else if (c == ';' && !dentroDeAspas) {
                campos.add(atual.toString());
                atual.setLength(0);
            } else {
                atual.append(c);
            }
        }
        campos.add(atual.toString());
        return campos;
    }
}
