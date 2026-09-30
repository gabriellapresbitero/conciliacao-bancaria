package io.github.gabriellapresbitero.conciliacao.leitura;

import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ConversorTest {

    @Test
    public void valoresEmVariosFormatos() {
        assertEquals(new BigDecimal("1234.56"), Conversor.valor("1.234,56"));
        assertEquals(new BigDecimal("-1234.56"), Conversor.valor("-1.234,56"));
        assertEquals(new BigDecimal("1234.56"), Conversor.valor("1234.56"));
        assertEquals(new BigDecimal("1234.56"), Conversor.valor("R$ 1.234,56"));
        assertEquals(new BigDecimal("-50.00"), Conversor.valor("(50,00)"));
        assertEquals(new BigDecimal("1000000"), Conversor.valor("1000000"));
    }

    @Test
    public void valorInvalido() {
        assertThrows(IllegalArgumentException.class, () -> Conversor.valor("dez reais"));
        assertThrows(IllegalArgumentException.class, () -> Conversor.valor(""));
    }

    @Test
    public void datasEmVariosFormatos() {
        LocalDate esperada = LocalDate.of(2025, 3, 10);
        assertEquals(esperada, Conversor.data("10/03/2025"));
        assertEquals(esperada, Conversor.data("2025-03-10"));
        assertEquals(esperada, Conversor.data("10-03-2025"));
        assertEquals(esperada, Conversor.data("20250310"));
    }

    @Test
    public void dataImpossivelNaoEAceita() {
        assertThrows(IllegalArgumentException.class, () -> Conversor.data("30/02/2025"));
    }
}
