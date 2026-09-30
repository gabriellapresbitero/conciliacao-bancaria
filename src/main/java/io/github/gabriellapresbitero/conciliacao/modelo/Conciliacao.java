package io.github.gabriellapresbitero.conciliacao.modelo;

import java.math.BigDecimal;
import java.util.List;

/**
 * Um grupo de lançamentos do banco e do sistema que se correspondem.
 * Nos tipos SO_NO_BANCO e SO_NO_SISTEMA, um dos lados fica vazio.
 */
public record Conciliacao(TipoConciliacao tipo, List<Lancamento> banco, List<Lancamento> sistema, String observacao) {

    public Conciliacao {
        banco = List.copyOf(banco);
        sistema = List.copyOf(sistema);
        observacao = observacao == null ? "" : observacao;
    }

    public BigDecimal totalBanco() {
        return soma(banco);
    }

    public BigDecimal totalSistema() {
        return soma(sistema);
    }

    /** Quanto o banco tem a mais (positivo) ou a menos (negativo) que o sistema neste grupo. */
    public BigDecimal diferenca() {
        return totalBanco().subtract(totalSistema());
    }

    public static BigDecimal soma(List<Lancamento> lancamentos) {
        return lancamentos.stream().map(Lancamento::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
