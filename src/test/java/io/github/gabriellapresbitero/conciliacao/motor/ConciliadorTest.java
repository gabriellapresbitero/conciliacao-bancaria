package io.github.gabriellapresbitero.conciliacao.motor;

import io.github.gabriellapresbitero.conciliacao.leitura.LeitorCsv;
import io.github.gabriellapresbitero.conciliacao.leitura.LeitorOfx;
import io.github.gabriellapresbitero.conciliacao.modelo.Conciliacao;
import io.github.gabriellapresbitero.conciliacao.modelo.Lancamento;
import io.github.gabriellapresbitero.conciliacao.modelo.Origem;
import io.github.gabriellapresbitero.conciliacao.modelo.TipoConciliacao;
import org.junit.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConciliadorTest {

    private final Conciliador conciliador = new Conciliador();
    private int proximoId = 1;

    private Lancamento banco(String data, String valor) {
        return banco(data, valor, "MOVIMENTO");
    }

    private Lancamento banco(String data, String valor, String descricao) {
        return new Lancamento("B" + proximoId++, LocalDate.parse(data), descricao, new BigDecimal(valor), Origem.BANCO);
    }

    private Lancamento sistema(String data, String valor) {
        return new Lancamento("S" + proximoId++, LocalDate.parse(data), "lançamento", new BigDecimal(valor), Origem.SISTEMA);
    }

    private ResultadoConciliacao conciliar(List<Lancamento> extrato, List<Lancamento> interno) {
        return conciliador.conciliar(extrato, interno);
    }

    @Test
    public void mesmoValorEMesmaDataEExata() {
        ResultadoConciliacao r = conciliar(List.of(banco("2025-03-10", "-100.00")), List.of(sistema("2025-03-10", "-100")));
        assertEquals(1, r.doTipo(TipoConciliacao.EXATA).size());
        assertTrue(r.pendencias().isEmpty());
    }

    @Test
    public void dataDentroDaToleranciaConcilia() {
        ResultadoConciliacao r = conciliar(List.of(banco("2025-03-10", "500")), List.of(sistema("2025-03-07", "500")));
        Conciliacao grupo = r.doTipo(TipoConciliacao.DATA_DIFERENTE).get(0);
        assertTrue(grupo.observacao(), grupo.observacao().contains("3 dias"));
    }

    @Test
    public void dataForaDaToleranciaNaoConcilia() {
        ResultadoConciliacao r = conciliar(List.of(banco("2025-03-10", "500")), List.of(sistema("2025-03-01", "500")));
        assertEquals(1, r.doTipo(TipoConciliacao.SO_NO_BANCO).size());
        assertEquals(1, r.doTipo(TipoConciliacao.SO_NO_SISTEMA).size());
    }

    @Test
    public void escolheADataMaisProximaQuandoHaDoisCandidatos() {
        Lancamento longe = banco("2025-03-12", "200");
        Lancamento perto = banco("2025-03-11", "200");
        ResultadoConciliacao r = conciliar(List.of(longe, perto), List.of(sistema("2025-03-10", "200")));
        assertEquals(List.of(perto), r.doTipo(TipoConciliacao.DATA_DIFERENTE).get(0).banco());
    }

    @Test
    public void cadaLancamentoSoEUsadoUmaVez() {
        // Dois pagamentos iguais no sistema e um só no banco: um deles fica pendente.
        ResultadoConciliacao r = conciliar(
                List.of(banco("2025-03-10", "-80")),
                List.of(sistema("2025-03-10", "-80"), sistema("2025-03-10", "-80")));
        assertEquals(1, r.doTipo(TipoConciliacao.EXATA).size());
        assertEquals(1, r.doTipo(TipoConciliacao.SO_NO_SISTEMA).size());
    }

    @Test
    public void variosDoSistemaSomamUmDoBanco() {
        // Três boletos pagos juntos aparecem como um único débito no extrato.
        ResultadoConciliacao r = conciliar(
                List.of(banco("2025-03-10", "-2730.50", "PAGTO LOTE")),
                List.of(sistema("2025-03-07", "-1500"), sistema("2025-03-08", "-780.50"),
                        sistema("2025-03-08", "-450"), sistema("2025-03-08", "-99")));
        Conciliacao grupo = r.doTipo(TipoConciliacao.AGRUPADA).get(0);
        assertEquals(1, grupo.banco().size());
        assertEquals(3, grupo.sistema().size());
        assertEquals(0, grupo.diferenca().signum());
        assertEquals(1, r.doTipo(TipoConciliacao.SO_NO_SISTEMA).size()); // o de -99 sobra
    }

    @Test
    public void variosDoBancoSomamUmDoSistema() {
        // Vendas de cartão caem separadas no banco, mas o caixa lança o total da semana.
        ResultadoConciliacao r = conciliar(
                List.of(banco("2025-03-11", "610.40"), banco("2025-03-11", "523.10"), banco("2025-03-12", "488.90")),
                List.of(sistema("2025-03-12", "1622.40")));
        Conciliacao grupo = r.doTipo(TipoConciliacao.AGRUPADA).get(0);
        assertEquals(3, grupo.banco().size());
        assertTrue(r.pendencias().isEmpty());
    }

    @Test
    public void agrupamentoNaoMisturaEntradasESaidas() {
        ResultadoConciliacao r = conciliar(
                List.of(banco("2025-03-10", "150"), banco("2025-03-10", "-50")),
                List.of(sistema("2025-03-10", "100")));
        assertTrue(r.doTipo(TipoConciliacao.AGRUPADA).isEmpty());
    }

    @Test
    public void valorProximoViraDivergencia() {
        ResultadoConciliacao r = conciliar(
                List.of(banco("2025-03-14", "-3531.20")), List.of(sistema("2025-03-12", "-3500")));
        Conciliacao grupo = r.doTipo(TipoConciliacao.VALOR_DIVERGENTE).get(0);
        assertEquals(new BigDecimal("-31.20"), grupo.diferenca());
        assertTrue(grupo.observacao(), grupo.observacao().contains("R$ 31,20"));
    }

    @Test
    public void valorMuitoDiferenteNaoViraDivergencia() {
        ResultadoConciliacao r = conciliar(List.of(banco("2025-03-14", "-4000")), List.of(sistema("2025-03-14", "-3500")));
        assertTrue(r.doTipo(TipoConciliacao.VALOR_DIVERGENTE).isEmpty());
    }

    @Test
    public void explicaSobrasDoBanco() {
        assertEquals("tarifa bancária não lançada no sistema",
                Conciliador.explicarSoNoBanco(banco("2025-03-15", "-89.90", "TARIFA CESTA SERVICOS")));
        assertEquals("encargo bancário não lançado no sistema",
                Conciliador.explicarSoNoBanco(banco("2025-03-31", "-3.21", "IOF")));
        assertEquals("sem correspondente no sistema",
                Conciliador.explicarSoNoBanco(banco("2025-03-25", "150", "PIX RECEBIDO")));
    }

    @Test
    public void lancamentoDepoisDoFimDoExtratoAindaNaoCompensou() {
        ResultadoConciliacao r = conciliar(List.of(banco("2025-03-31", "10")), List.of(sistema("2025-04-02", "-640")));
        String observacao = r.doTipo(TipoConciliacao.SO_NO_SISTEMA).get(0).observacao();
        assertTrue(observacao, observacao.contains("ainda não compensado"));
    }

    @Test
    public void nenhumLancamentoSePerdeOuSeRepete() {
        List<Lancamento> extrato = List.of(banco("2025-03-01", "10"), banco("2025-03-02", "-20"), banco("2025-03-03", "30"));
        List<Lancamento> interno = List.of(sistema("2025-03-01", "10"), sistema("2025-03-05", "-7"));
        ResultadoConciliacao r = conciliar(extrato, interno);
        Set<Lancamento> vistos = new HashSet<>();
        for (Conciliacao g : r.grupos()) {
            g.banco().forEach(l -> assertTrue("repetido: " + l, vistos.add(l)));
            g.sistema().forEach(l -> assertTrue("repetido: " + l, vistos.add(l)));
        }
        assertEquals(5, vistos.size());
    }

    @Test
    public void exemploDoRepositorioFechaADiferenca() throws Exception {
        List<Lancamento> extrato = new LeitorOfx().ler(Path.of("exemplos/extrato_marco.ofx"));
        List<Lancamento> interno = new LeitorCsv().ler(Path.of("exemplos/sistema_marco.csv"), Origem.SISTEMA);
        ResultadoConciliacao r = conciliador.conciliar(extrato, interno);

        Map<TipoConciliacao, Integer> porTipo = r.lancamentosPorTipo();
        assertEquals(Integer.valueOf(10), porTipo.get(TipoConciliacao.EXATA));
        assertEquals(Integer.valueOf(2), porTipo.get(TipoConciliacao.DATA_DIFERENTE));
        assertEquals(Integer.valueOf(8), porTipo.get(TipoConciliacao.AGRUPADA));
        assertEquals(Integer.valueOf(2), porTipo.get(TipoConciliacao.VALOR_DIVERGENTE));
        assertEquals(Integer.valueOf(4), porTipo.get(TipoConciliacao.SO_NO_BANCO));
        assertEquals(Integer.valueOf(2), porTipo.get(TipoConciliacao.SO_NO_SISTEMA));

        // A soma das diferenças das pendências explica exatamente a diferença total.
        BigDecimal somaPendencias = r.pendencias().stream()
                .map(Conciliacao::diferenca).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("-1421.97"), r.diferenca());
        assertEquals(r.diferenca(), somaPendencias);
    }
}
