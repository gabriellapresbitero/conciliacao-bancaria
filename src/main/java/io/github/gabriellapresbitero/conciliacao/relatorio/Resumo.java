package io.github.gabriellapresbitero.conciliacao.relatorio;

import io.github.gabriellapresbitero.conciliacao.Formatos;
import io.github.gabriellapresbitero.conciliacao.modelo.Conciliacao;
import io.github.gabriellapresbitero.conciliacao.modelo.Lancamento;
import io.github.gabriellapresbitero.conciliacao.modelo.TipoConciliacao;
import io.github.gabriellapresbitero.conciliacao.motor.ResultadoConciliacao;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Resumo em texto para mostrar no terminal. */
public class Resumo {

    public String gerar(ResultadoConciliacao resultado) {
        StringBuilder sb = new StringBuilder();
        sb.append("=".repeat(72)).append('\n');
        sb.append("CONCILIAÇÃO BANCÁRIA\n");
        sb.append("=".repeat(72)).append('\n');

        Map<TipoConciliacao, Integer> porTipo = resultado.lancamentosPorTipo();
        for (TipoConciliacao tipo : TipoConciliacao.values()) {
            sb.append(String.format("%-42s %6d lançamento(s)%n", tipo.descricao(), porTipo.get(tipo)));
        }
        sb.append('\n');
        sb.append(String.format(Locale.ROOT, "Conciliado automaticamente: %.1f%% dos lançamentos%n",
                resultado.percentualConciliado()).replace('.', ','));
        sb.append(String.format("Movimento no banco:   %s%n", Formatos.moeda(resultado.totalBanco())));
        sb.append(String.format("Movimento no sistema: %s%n", Formatos.moeda(resultado.totalSistema())));
        sb.append(String.format("Diferença:            %s%n", Formatos.moeda(resultado.diferenca())));

        List<Conciliacao> pendencias = resultado.pendencias();
        if (pendencias.isEmpty()) {
            sb.append("\nTudo conciliado. Nenhuma pendência.\n");
            return sb.toString();
        }

        sb.append("\nPENDÊNCIAS (explicam a diferença acima)\n");
        sb.append("-".repeat(72)).append('\n');
        for (Conciliacao p : pendencias) {
            Lancamento l = p.banco().isEmpty() ? p.sistema().get(0) : p.banco().get(0);
            sb.append(String.format("%s  %-30.30s %15s  %s%n",
                    l.data().format(Formatos.DATA), l.descricao(), Formatos.moeda(p.diferenca()), p.observacao()));
        }
        return sb.toString();
    }
}
