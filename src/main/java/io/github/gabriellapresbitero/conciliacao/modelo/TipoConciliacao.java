package io.github.gabriellapresbitero.conciliacao.modelo;

/** Como um grupo de lançamentos foi (ou não foi) conciliado. */
public enum TipoConciliacao {
    EXATA("Conciliado: mesmo valor e mesma data"),
    DATA_DIFERENTE("Conciliado: mesmo valor, data próxima"),
    AGRUPADA("Conciliado: vários lançamentos somam um só"),
    VALOR_DIVERGENTE("Divergência de valor"),
    SO_NO_BANCO("Só no extrato do banco"),
    SO_NO_SISTEMA("Só no sistema");

    private final String descricao;

    TipoConciliacao(String descricao) {
        this.descricao = descricao;
    }

    public String descricao() {
        return descricao;
    }

    /** Os três primeiros tipos fecham sem diferença; os outros precisam de atenção. */
    public boolean conciliado() {
        return this == EXATA || this == DATA_DIFERENTE || this == AGRUPADA;
    }
}
