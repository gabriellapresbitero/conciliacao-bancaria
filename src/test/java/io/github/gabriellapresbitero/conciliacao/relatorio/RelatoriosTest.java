package io.github.gabriellapresbitero.conciliacao.relatorio;

import io.github.gabriellapresbitero.conciliacao.Formatos;
import io.github.gabriellapresbitero.conciliacao.modelo.Conciliacao;
import io.github.gabriellapresbitero.conciliacao.modelo.Lancamento;
import io.github.gabriellapresbitero.conciliacao.modelo.Origem;
import io.github.gabriellapresbitero.conciliacao.modelo.TipoConciliacao;
import io.github.gabriellapresbitero.conciliacao.motor.ResultadoConciliacao;
import org.junit.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RelatoriosTest {

    private static final Lancamento BANCO = new Lancamento("B1", LocalDate.of(2025, 3, 10), "PIX; cliente",
            new BigDecimal("1234.5"), Origem.BANCO);
    private static final Lancamento SISTEMA = new Lancamento("S1", LocalDate.of(2025, 3, 10), "Venda",
            new BigDecimal("1234.5"), Origem.SISTEMA);
    private static final Lancamento TARIFA = new Lancamento("B2", LocalDate.of(2025, 3, 15), "TARIFA",
            new BigDecimal("-9.9"), Origem.BANCO);

    private static final ResultadoConciliacao RESULTADO = new ResultadoConciliacao(List.of(
            new Conciliacao(TipoConciliacao.EXATA, List.of(BANCO), List.of(SISTEMA), ""),
            new Conciliacao(TipoConciliacao.SO_NO_BANCO, List.of(TARIFA), List.of(), "tarifa")));

    @Test
    public void csvTemUmaLinhaPorLancamentoComGrupo() {
        List<String> linhas = new RelatorioCsv().linhas(RESULTADO);
        assertEquals(RelatorioCsv.CABECALHO, linhas.get(0));
        assertEquals(4, linhas.size());
        assertEquals("1;Conciliado: mesmo valor e mesma data;sim;BANCO;B1;10/03/2025;\"PIX; cliente\";1234,50;",
                linhas.get(1));
        assertTrue(linhas.get(3).startsWith("2;Só no extrato do banco;não;BANCO;B2"));
    }

    @Test
    public void salvaComBomParaOExcel() throws Exception {
        Path arquivo = Files.createTempDirectory("conc").resolve("saida/conciliacao.csv");
        new RelatorioCsv().salvar(RESULTADO, arquivo);
        String conteudo = Files.readString(arquivo, StandardCharsets.UTF_8);
        assertTrue(conteudo.startsWith("﻿grupo;"));
    }

    @Test
    public void resumoMostraTotaisEPendencias() {
        String texto = new Resumo().gerar(RESULTADO);
        assertTrue(texto, texto.contains("Diferença:            -R$ 9,90"));
        assertTrue(texto, texto.contains("66,7%"));
        assertTrue(texto, texto.contains("tarifa"));
    }

    @Test
    public void formatosBrasileiros() {
        assertEquals("R$ 1.234,50", Formatos.moeda(new BigDecimal("1234.5")));
        assertEquals("-R$ 0,01", Formatos.moeda(new BigDecimal("-0.01")));
        assertEquals("-9,90", Formatos.numeroCsv(new BigDecimal("-9.9")));
    }
}
