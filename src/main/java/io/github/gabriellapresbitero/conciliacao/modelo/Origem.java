package io.github.gabriellapresbitero.conciliacao.modelo;

/** De onde veio um lançamento. */
public enum Origem {
    /** Extrato do banco (o que realmente entrou e saiu da conta). */
    BANCO,
    /** Sistema da empresa (contas a pagar e a receber, ERP, planilha). */
    SISTEMA
}
