package io.github.gabriellapresbitero.conciliacao;

import io.github.gabriellapresbitero.conciliacao.leitura.ArquivoInvalidoException;
import io.github.gabriellapresbitero.conciliacao.leitura.LeitorCsv;
import io.github.gabriellapresbitero.conciliacao.leitura.LeitorOfx;
import io.github.gabriellapresbitero.conciliacao.modelo.Lancamento;
import io.github.gabriellapresbitero.conciliacao.modelo.Origem;
import io.github.gabriellapresbitero.conciliacao.motor.Conciliador;
import io.github.gabriellapresbitero.conciliacao.motor.Configuracao;
import io.github.gabriellapresbitero.conciliacao.motor.ResultadoConciliacao;
import io.github.gabriellapresbitero.conciliacao.relatorio.RelatorioCsv;
import io.github.gabriellapresbitero.conciliacao.relatorio.Resumo;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Linha de comando.
 *
 * <pre>
 * java -jar target/conciliacao.jar --extrato exemplos/extrato.ofx --sistema exemplos/sistema.csv
 * </pre>
 */
public class Main {

    private static final String USO = """
            Uso: java -jar conciliacao.jar --extrato ARQUIVO --sistema ARQUIVO [opções]

              --extrato ARQUIVO        extrato do banco (.ofx ou .csv)
              --sistema ARQUIVO        lançamentos do sistema da empresa (.csv)
              --saida ARQUIVO          CSV detalhado (padrão: conciliacao.csv)
              --tolerancia-dias N      diferença máxima de datas (padrão: 3)
              --tolerancia-valor P     diferença máxima de valor em %% (padrão: 5)
            """;

    public static void main(String[] args) {
        // Força UTF-8 na saída, para os acentos aparecerem certos em qualquer terminal moderno.
        PrintStream saida = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        PrintStream erro = new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);
        System.exit(executar(args, saida, erro));
    }

    static int executar(String[] args, PrintStream saida, PrintStream erro) {
        Path extrato = null;
        Path sistema = null;
        Path destino = Path.of("conciliacao.csv");
        int toleranciaDias = Configuracao.PADRAO.toleranciaDias();
        BigDecimal toleranciaValor = Configuracao.PADRAO.toleranciaPercentual();

        try {
            for (int i = 0; i < args.length; i++) {
                String opcao = args[i];
                if (opcao.equals("--ajuda") || opcao.equals("-h")) {
                    saida.printf(USO);
                    return 0;
                }
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("falta o valor de " + opcao);
                }
                String valor = args[++i];
                switch (opcao) {
                    case "--extrato" -> extrato = Path.of(valor);
                    case "--sistema" -> sistema = Path.of(valor);
                    case "--saida" -> destino = Path.of(valor);
                    case "--tolerancia-dias" -> toleranciaDias = Integer.parseInt(valor);
                    case "--tolerancia-valor" -> toleranciaValor = new BigDecimal(valor.replace(',', '.'));
                    default -> throw new IllegalArgumentException("opção desconhecida: " + opcao);
                }
            }
            if (extrato == null || sistema == null) {
                throw new IllegalArgumentException("informe --extrato e --sistema");
            }

            List<Lancamento> banco = extrato.toString().toLowerCase(Locale.ROOT).endsWith(".ofx")
                    ? new LeitorOfx().ler(extrato)
                    : new LeitorCsv().ler(extrato, Origem.BANCO);
            List<Lancamento> interno = new LeitorCsv().ler(sistema, Origem.SISTEMA);

            Configuracao config = new Configuracao(toleranciaDias, toleranciaValor, Configuracao.PADRAO.maxItensAgrupamento());
            ResultadoConciliacao resultado = new Conciliador(config).conciliar(banco, interno);

            new RelatorioCsv().salvar(resultado, destino);
            saida.print(new Resumo().gerar(resultado));
            saida.println("\nDetalhes salvos em: " + destino);
            return resultado.pendencias().isEmpty() ? 0 : 2;
        } catch (NoSuchFileException e) {
            erro.println("Erro: arquivo não encontrado: " + e.getFile());
        } catch (ArquivoInvalidoException | IllegalArgumentException e) {
            erro.println("Erro: " + e.getMessage());
            erro.printf(USO);
        } catch (IOException e) {
            erro.println("Erro ao ler ou gravar arquivo: " + e.getMessage());
        }
        return 1;
    }
}
