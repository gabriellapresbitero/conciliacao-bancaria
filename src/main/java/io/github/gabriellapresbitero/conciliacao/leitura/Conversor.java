package io.github.gabriellapresbitero.conciliacao.leitura;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;

/** Converte textos em valores e datas, aceitando os formatos comuns no Brasil. */
public final class Conversor {

    private static final List<DateTimeFormatter> FORMATOS_DATA = List.of(
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT));

    private Conversor() {
    }

    /**
     * Aceita "1.234,56", "-1.234,56", "1234.56", "R$ 1.234,56" e "(1.234,56)".
     * Os parênteses são o jeito contábil de escrever um valor negativo.
     */
    public static BigDecimal valor(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("valor vazio");
        }
        String limpo = texto.replace("R$", "").replace(" ", "").replace(" ", "").strip();
        boolean negativo = false;
        if (limpo.startsWith("(") && limpo.endsWith(")")) {
            negativo = true;
            limpo = limpo.substring(1, limpo.length() - 1);
        }
        if (limpo.contains(",")) {
            // Formato brasileiro: ponto separa milhar, vírgula separa os centavos.
            limpo = limpo.replace(".", "").replace(",", ".");
        }
        try {
            BigDecimal valor = new BigDecimal(limpo);
            return negativo ? valor.negate() : valor;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("valor inválido: \"" + texto + "\"");
        }
    }

    public static LocalDate data(String texto) {
        String limpo = texto == null ? "" : texto.strip();
        for (DateTimeFormatter formato : FORMATOS_DATA) {
            try {
                return LocalDate.parse(limpo, formato);
            } catch (DateTimeParseException ignorada) {
                // tenta o próximo formato
            }
        }
        throw new IllegalArgumentException("data inválida: \"" + texto + "\"");
    }
}
