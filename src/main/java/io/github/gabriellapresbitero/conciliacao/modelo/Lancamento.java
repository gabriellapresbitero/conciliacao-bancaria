package io.github.gabriellapresbitero.conciliacao.modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Uma movimentação financeira: entrada (valor positivo) ou saída (valor negativo).
 *
 * <p>Dinheiro é sempre {@link BigDecimal}, nunca {@code double}. Com double,
 * {@code 0.1 + 0.2} dá {@code 0.30000000000000004}, e numa conciliação
 * um centavo de diferença já quebra a comparação.
 */
public record Lancamento(String id, LocalDate data, String descricao, BigDecimal valor, Origem origem) {

    public Lancamento {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(valor, "valor");
        Objects.requireNonNull(origem, "origem");
        descricao = descricao == null ? "" : descricao.strip();
        // Escala fixa de 2 casas: assim 10.0 e 10.00 são considerados iguais.
        valor = valor.setScale(2, RoundingMode.HALF_EVEN);
    }

    public boolean mesmoSinal(Lancamento outro) {
        return valor.signum() == outro.valor.signum();
    }
}
