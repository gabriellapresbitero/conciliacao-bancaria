package io.github.gabriellapresbitero.conciliacao.leitura;

import io.github.gabriellapresbitero.conciliacao.modelo.Lancamento;
import io.github.gabriellapresbitero.conciliacao.modelo.Origem;
import org.junit.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class LeitorOfxTest {

    private static final String OFX = """
            OFXHEADER:100
            DATA:OFXSGML
            <OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><BANKTRANLIST>
            <STMTTRN>
            <TRNTYPE>DEBIT
            <DTPOSTED>20250310120000[-3:BRT]
            <TRNAMT>-150.00
            <FITID>ABC123
            <MEMO>PIX ENVIADO
            </STMTTRN>
            <STMTTRN>
            <TRNTYPE>CREDIT
            <DTPOSTED>20250311
            <TRNAMT>99.9
            <FITID>ABC124
            <NAME>CLIENTE X</NAME>
            </STMTTRN>
            </BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>
            """;

    private final LeitorOfx leitor = new LeitorOfx();

    @Test
    public void leTransacoes() {
        List<Lancamento> lidos = leitor.ler(OFX, "extrato.ofx");
        assertEquals(2, lidos.size());

        Lancamento primeiro = lidos.get(0);
        assertEquals("ABC123", primeiro.id());
        assertEquals(LocalDate.of(2025, 3, 10), primeiro.data());
        assertEquals(new BigDecimal("-150.00"), primeiro.valor());
        assertEquals("PIX ENVIADO", primeiro.descricao());
        assertEquals(Origem.BANCO, primeiro.origem());
    }

    @Test
    public void usaNameQuandoNaoTemMemoEAceitaTagFechada() {
        Lancamento segundo = leitor.ler(OFX, "extrato.ofx").get(1);
        assertEquals("CLIENTE X", segundo.descricao());
        assertEquals(new BigDecimal("99.90"), segundo.valor());
    }

    @Test
    public void arquivoSemTransacoes() {
        assertThrows(ArquivoInvalidoException.class, () -> leitor.ler("<OFX></OFX>", "vazio.ofx"));
    }

    @Test
    public void ofxECsvDeExemploTemOsMesmosLancamentos() throws Exception {
        List<Lancamento> doOfx = leitor.ler(Path.of("exemplos/extrato_marco.ofx"));
        List<Lancamento> doCsv = new LeitorCsv().ler(Path.of("exemplos/extrato_marco.csv"), Origem.BANCO);
        assertEquals(doCsv, doOfx);
    }
}
