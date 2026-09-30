package io.github.gabriellapresbitero.conciliacao.motor;

import io.github.gabriellapresbitero.conciliacao.modelo.Conciliacao;
import io.github.gabriellapresbitero.conciliacao.modelo.TipoConciliacao;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Resultado final: todos os grupos, com totais para o resumo. */
public record ResultadoConciliacao(List<Conciliacao> grupos) {

    public ResultadoConciliacao {
        grupos = List.copyOf(grupos);
    }

    public List<Conciliacao> doTipo(TipoConciliacao tipo) {
        return grupos.stream().filter(g -> g.tipo() == tipo).toList();
    }

    public List<Conciliacao> pendencias() {
        return grupos.stream().filter(g -> !g.tipo().conciliado()).toList();
    }

    /** Quantos lançamentos (banco + sistema) caíram em cada tipo. */
    public Map<TipoConciliacao, Integer> lancamentosPorTipo() {
        Map<TipoConciliacao, Integer> contagem = new EnumMap<>(TipoConciliacao.class);
        for (TipoConciliacao tipo : TipoConciliacao.values()) {
            contagem.put(tipo, 0);
        }
        for (Conciliacao g : grupos) {
            contagem.merge(g.tipo(), g.banco().size() + g.sistema().size(), Integer::sum);
        }
        return contagem;
    }

    public int totalLancamentos() {
        return grupos.stream().mapToInt(g -> g.banco().size() + g.sistema().size()).sum();
    }

    /** Percentual dos lançamentos que foram conciliados sem diferença (0 a 100). */
    public double percentualConciliado() {
        int total = totalLancamentos();
        if (total == 0) {
            return 100.0;
        }
        int conciliados = grupos.stream()
                .filter(g -> g.tipo().conciliado())
                .mapToInt(g -> g.banco().size() + g.sistema().size())
                .sum();
        return 100.0 * conciliados / total;
    }

    public BigDecimal totalBanco() {
        return grupos.stream().map(Conciliacao::totalBanco).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal totalSistema() {
        return grupos.stream().map(Conciliacao::totalSistema).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Diferença entre o movimento do banco e o do sistema.
     * É sempre igual à soma das diferenças das pendências, porque os grupos
     * conciliados têm diferença zero. É isso que "fecha" a conciliação.
     */
    public BigDecimal diferenca() {
        return totalBanco().subtract(totalSistema());
    }
}
