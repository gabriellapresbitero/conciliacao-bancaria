package io.github.gabriellapresbitero.conciliacao.leitura;

import io.github.gabriellapresbitero.conciliacao.modelo.Lancamento;
import io.github.gabriellapresbitero.conciliacao.modelo.Origem;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class LeitorCsvTest {

    private final LeitorCsv leitor = new LeitorCsv();

    @Test
    public void leLancamentosComFormatosBrasileiros() {
        String csv = """
                data;descricao;valor
                10/03/2025;Venda;1.234,56
                2025-03-11;Compra;-80,10
                """;
        List<Lancamento> lidos = leitor.ler(csv, Origem.SISTEMA, "teste.csv");
        assertEquals(2, lidos.size());
        assertEquals(LocalDate.of(2025, 3, 10), lidos.get(0).data());
        assertEquals(new BigDecimal("1234.56"), lidos.get(0).valor());
        assertEquals(new BigDecimal("-80.10"), lidos.get(1).valor());
        assertEquals(Origem.SISTEMA, lidos.get(1).origem());
    }

    @Test
    public void usaDocumentoComoIdOuGeraUmPelaLinha() {
        String csv = "data;descricao;valor;documento\n10/03/2025;A;10,00;NF-1\n10/03/2025;B;20,00;\n";
        List<Lancamento> lidos = leitor.ler(csv, Origem.BANCO, "teste.csv");
        assertEquals("NF-1", lidos.get(0).id());
        assertEquals("B-3", lidos.get(1).id());
    }

    @Test
    public void aceitaCabecalhoComAcentoEmOutraOrdem() {
        String csv = "Valor;Descrição;Data\n10,00;Café;01/03/2025\n";
        Lancamento lido = leitor.ler(csv, Origem.SISTEMA, "teste.csv").get(0);
        assertEquals("Café", lido.descricao());
        assertEquals(new BigDecimal("10.00"), lido.valor());
    }

    @Test
    public void respeitaPontoEVirgulaDentroDeAspas() {
        List<String> campos = LeitorCsv.dividir("01/03/2025;\"Pix; João \"\"Zé\"\"\";10,00");
        assertEquals(List.of("01/03/2025", "Pix; João \"Zé\"", "10,00"), campos);
    }

    @Test
    public void ignoraLinhasEmBrancoEBom() {
        String csv = "﻿data;descricao;valor\n\n01/03/2025;A;1,00\n\n";
        assertEquals(1, leitor.ler(csv, Origem.SISTEMA, "teste.csv").size());
    }

    @Test
    public void erroIndicaArquivoELinha() {
        String csv = "data;descricao;valor\n01/03/2025;A;1,00\n31/02/2025;B;2,00\n";
        ArquivoInvalidoException erro = assertThrows(ArquivoInvalidoException.class,
                () -> leitor.ler(csv, Origem.SISTEMA, "sistema.csv"));
        assertTrue(erro.getMessage(), erro.getMessage().contains("sistema.csv, linha 3"));
        assertTrue(erro.getMessage(), erro.getMessage().contains("data inválida"));
    }

    @Test
    public void erroQuandoFaltaColuna() {
        ArquivoInvalidoException erro = assertThrows(ArquivoInvalidoException.class,
                () -> leitor.ler("data;valor\n01/03/2025;1,00\n", Origem.SISTEMA, "x.csv"));
        assertTrue(erro.getMessage().contains("descricao"));
    }
}
