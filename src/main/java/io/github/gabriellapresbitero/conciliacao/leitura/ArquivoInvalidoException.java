package io.github.gabriellapresbitero.conciliacao.leitura;

/** Erro de leitura com a indicação de onde o problema está, para o usuário conseguir corrigir. */
public class ArquivoInvalidoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ArquivoInvalidoException(String mensagem) {
        super(mensagem);
    }

    public ArquivoInvalidoException(String arquivo, int linha, String mensagem) {
        super(arquivo + ", linha " + linha + ": " + mensagem);
    }
}
