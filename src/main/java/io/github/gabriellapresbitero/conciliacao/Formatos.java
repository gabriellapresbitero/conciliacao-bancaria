package io.github.gabriellapresbitero.conciliacao;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Formatação no padrão brasileiro. */
public final class Formatos {

    public static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private Formatos() {
    }

    /** 1234.5 -> "R$ 1.234,50"; -50 -> "-R$ 50,00" */
    public static String moeda(BigDecimal valor) {
        // DecimalFormat não é thread-safe, por isso criamos um novo a cada chamada.
        DecimalFormat formato = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.of("pt", "BR")));
        String texto = "R$ " + formato.format(valor.abs());
        return valor.signum() < 0 ? "-" + texto : texto;
    }

    /** Valor para CSV: sem "R$" e com vírgula decimal, como o Excel em português espera. */
    public static String numeroCsv(BigDecimal valor) {
        return valor.setScale(2).toPlainString().replace('.', ',');
    }
}
