package io.github.gabriellapresbitero.conciliacao;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MainTest {

    private final ByteArrayOutputStream saida = new ByteArrayOutputStream();
    private final ByteArrayOutputStream erro = new ByteArrayOutputStream();

    private int rodar(String... args) {
        return Main.executar(args,
                new PrintStream(saida, true, StandardCharsets.UTF_8),
                new PrintStream(erro, true, StandardCharsets.UTF_8));
    }

    @Test
    public void rodaOExemploEGeraOCsv() throws Exception {
        Path destino = Files.createTempDirectory("main").resolve("conciliacao.csv");
        int codigo = rodar("--extrato", "exemplos/extrato_marco.ofx", "--sistema", "exemplos/sistema_marco.csv",
                "--saida", destino.toString());
        assertEquals("o exemplo tem pendências, então o código de saída é 2", 2, codigo);
        assertTrue(Files.exists(destino));
        assertTrue(saida.toString(StandardCharsets.UTF_8).contains("PENDÊNCIAS"));
    }

    @Test
    public void semPendenciasOCodigoEZero() throws Exception {
        Path pasta = Files.createTempDirectory("main");
        Files.writeString(pasta.resolve("b.csv"), "data;descricao;valor\n01/03/2025;A;10,00\n");
        Files.writeString(pasta.resolve("s.csv"), "data;descricao;valor\n02/03/2025;A;10,00\n");
        int codigo = rodar("--extrato", pasta.resolve("b.csv").toString(), "--sistema", pasta.resolve("s.csv").toString(),
                "--saida", pasta.resolve("c.csv").toString());
        assertEquals(0, codigo);
        assertTrue(saida.toString(StandardCharsets.UTF_8).contains("Tudo conciliado"));
    }

    @Test
    public void argumentosInvalidos() {
        assertEquals(1, rodar("--extrato", "x.ofx"));
        assertTrue(erro.toString(StandardCharsets.UTF_8).contains("informe --extrato e --sistema"));
    }

    @Test
    public void arquivoInexistente() {
        assertEquals(1, rodar("--extrato", "nao-existe.ofx", "--sistema", "nao-existe.csv"));
        assertTrue(erro.toString(StandardCharsets.UTF_8).contains("não encontrado"));
    }

    @Test
    public void ajuda() {
        assertEquals(0, rodar("--ajuda"));
        assertTrue(saida.toString(StandardCharsets.UTF_8).contains("--tolerancia-dias"));
    }
}
