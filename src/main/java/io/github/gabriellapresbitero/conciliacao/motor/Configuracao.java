package io.github.gabriellapresbitero.conciliacao.motor;

import java.math.BigDecimal;

/**
 * Parâmetros da conciliação.
 *
 * @param toleranciaDias          diferença máxima de datas (em dias corridos) entre o banco e o sistema.
 *                                Um boleto pago na sexta costuma aparecer no extrato só na segunda.
 * @param toleranciaPercentual    diferença máxima de valor, em %, para apontar uma "divergência de valor"
 *                                (ex.: juros ou desconto) em vez de dois lançamentos sem par.
 * @param maxItensAgrupamento     quantos lançamentos, no máximo, podem somar um único lançamento do outro lado.
 */
public record Configuracao(int toleranciaDias, BigDecimal toleranciaPercentual, int maxItensAgrupamento) {

    public static final Configuracao PADRAO = new Configuracao(3, new BigDecimal("5"), 4);

    public Configuracao {
        if (toleranciaDias < 0) {
            throw new IllegalArgumentException("toleranciaDias não pode ser negativa");
        }
        if (toleranciaPercentual.signum() < 0) {
            throw new IllegalArgumentException("toleranciaPercentual não pode ser negativa");
        }
        if (maxItensAgrupamento < 2) {
            throw new IllegalArgumentException("maxItensAgrupamento precisa ser pelo menos 2");
        }
    }
}
