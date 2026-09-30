package io.github.gabriellapresbitero.conciliacao.motor;

import io.github.gabriellapresbitero.conciliacao.Formatos;
import io.github.gabriellapresbitero.conciliacao.modelo.Conciliacao;
import io.github.gabriellapresbitero.conciliacao.modelo.Lancamento;
import io.github.gabriellapresbitero.conciliacao.modelo.TipoConciliacao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Faz a conciliação em etapas, da mais segura para a menos segura.
 * Um lançamento conciliado numa etapa sai da lista e não é mais usado.
 *
 * <ol>
 *   <li>Exata: mesmo valor, mesma data.</li>
 *   <li>Data diferente: mesmo valor, datas a até N dias.</li>
 *   <li>Agrupada: vários lançamentos de um lado somam exatamente um do outro
 *       (ex.: o sistema tem 3 boletos e o banco mostra um único débito "PAGAMENTO EM LOTE").</li>
 *   <li>Divergência de valor: valores próximos (até X%), provável juros, multa ou desconto.</li>
 *   <li>Sobras: o que ficou só no banco ou só no sistema.</li>
 * </ol>
 */
public class Conciliador {

    /** Limite de candidatos na busca de agrupamentos, para a busca não explodir. */
    private static final int MAX_CANDIDATOS_AGRUPAMENTO = 20;

    private static final Comparator<Lancamento> POR_DATA = Comparator
            .comparing(Lancamento::data)
            .thenComparing(Lancamento::id);

    private final Configuracao config;

    public Conciliador() {
        this(Configuracao.PADRAO);
    }

    public Conciliador(Configuracao config) {
        this.config = config;
    }

    public ResultadoConciliacao conciliar(List<Lancamento> extrato, List<Lancamento> sistema) {
        List<Lancamento> banco = new ArrayList<>(extrato);
        List<Lancamento> interno = new ArrayList<>(sistema);
        banco.sort(POR_DATA);
        interno.sort(POR_DATA);
        List<Conciliacao> grupos = new ArrayList<>();

        parearPorValor(banco, interno, 0, TipoConciliacao.EXATA, grupos);
        parearPorValor(banco, interno, config.toleranciaDias(), TipoConciliacao.DATA_DIFERENTE, grupos);
        agrupar(banco, interno, true, grupos);
        agrupar(interno, banco, false, grupos);
        parearDivergentes(banco, interno, grupos);

        Optional<LocalDate> fimDoExtrato = extrato.stream().map(Lancamento::data).max(LocalDate::compareTo);
        for (Lancamento b : banco) {
            grupos.add(new Conciliacao(TipoConciliacao.SO_NO_BANCO, List.of(b), List.of(), explicarSoNoBanco(b)));
        }
        for (Lancamento s : interno) {
            grupos.add(new Conciliacao(TipoConciliacao.SO_NO_SISTEMA, List.of(), List.of(s),
                    explicarSoNoSistema(s, fimDoExtrato)));
        }

        grupos.sort(Comparator.comparing(Conciliador::primeiraData));
        return new ResultadoConciliacao(grupos);
    }

    // ---------------------------------------------------------------- etapas 1 e 2

    private void parearPorValor(List<Lancamento> banco, List<Lancamento> interno, int toleranciaDias,
                                TipoConciliacao tipo, List<Conciliacao> grupos) {
        for (Lancamento s : List.copyOf(interno)) {
            Lancamento melhor = null;
            long menorDistancia = Long.MAX_VALUE;
            for (Lancamento b : banco) {
                long distancia = dias(b, s);
                if (b.valor().compareTo(s.valor()) == 0 && distancia <= toleranciaDias && distancia < menorDistancia) {
                    melhor = b;
                    menorDistancia = distancia;
                }
            }
            if (melhor != null) {
                banco.remove(melhor);
                interno.remove(s);
                String observacao = menorDistancia == 0 ? "" : String.format(
                        "banco em %s, sistema em %s (%d dia%s)", melhor.data().format(Formatos.DATA),
                        s.data().format(Formatos.DATA), menorDistancia, menorDistancia > 1 ? "s" : "");
                grupos.add(new Conciliacao(tipo, List.of(melhor), List.of(s), observacao));
            }
        }
    }

    // ---------------------------------------------------------------- etapa 3

    /**
     * Procura combinações de 2 a N lançamentos em {@code muitos} que somem exatamente
     * um lançamento de {@code unicos}.
     */
    private void agrupar(List<Lancamento> muitos, List<Lancamento> unicos, boolean muitosSaoDoBanco,
                         List<Conciliacao> grupos) {
        for (Lancamento alvo : List.copyOf(unicos)) {
            List<Lancamento> candidatos = muitos.stream()
                    .filter(c -> c.mesmoSinal(alvo))
                    .filter(c -> c.valor().abs().compareTo(alvo.valor().abs()) < 0)
                    .filter(c -> dias(c, alvo) <= config.toleranciaDias())
                    .sorted(Comparator.comparingLong((Lancamento c) -> dias(c, alvo)).thenComparing(POR_DATA))
                    .limit(MAX_CANDIDATOS_AGRUPAMENTO)
                    .toList();
            if (candidatos.size() < 2) {
                continue;
            }

            for (int tamanho = 2; tamanho <= config.maxItensAgrupamento(); tamanho++) {
                Deque<Lancamento> escolhidos = new ArrayDeque<>();
                if (buscarCombinacao(candidatos, 0, tamanho, alvo.valor().abs(), escolhidos)) {
                    List<Lancamento> parte = new ArrayList<>(escolhidos);
                    parte.sort(POR_DATA);
                    muitos.removeAll(parte);
                    unicos.remove(alvo);
                    String observacao = String.format("%d lançamentos do %s somam 1 do %s",
                            parte.size(), muitosSaoDoBanco ? "banco" : "sistema", muitosSaoDoBanco ? "sistema" : "banco");
                    grupos.add(muitosSaoDoBanco
                            ? new Conciliacao(TipoConciliacao.AGRUPADA, parte, List.of(alvo), observacao)
                            : new Conciliacao(TipoConciliacao.AGRUPADA, List.of(alvo), parte, observacao));
                    break;
                }
            }
        }
    }

    /**
     * Busca recursiva (backtracking): tenta escolher {@code restantes} itens a partir de
     * {@code inicio} cuja soma dos valores absolutos seja exatamente {@code falta}.
     */
    private boolean buscarCombinacao(List<Lancamento> candidatos, int inicio, int restantes,
                                     BigDecimal falta, Deque<Lancamento> escolhidos) {
        if (restantes == 0) {
            return falta.signum() == 0;
        }
        for (int i = inicio; i <= candidatos.size() - restantes; i++) {
            BigDecimal valor = candidatos.get(i).valor().abs();
            int comparacao = valor.compareTo(falta);
            // O último item precisa fechar a conta exatamente; os anteriores precisam
            // deixar alguma coisa para os próximos (todos têm o mesmo sinal).
            if (restantes == 1 ? comparacao != 0 : comparacao >= 0) {
                continue;
            }
            escolhidos.push(candidatos.get(i));
            if (buscarCombinacao(candidatos, i + 1, restantes - 1, falta.subtract(valor), escolhidos)) {
                return true;
            }
            escolhidos.pop();
        }
        return false;
    }

    // ---------------------------------------------------------------- etapa 4

    private void parearDivergentes(List<Lancamento> banco, List<Lancamento> interno, List<Conciliacao> grupos) {
        for (Lancamento s : List.copyOf(interno)) {
            BigDecimal limite = s.valor().abs().multiply(config.toleranciaPercentual())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_EVEN);
            Lancamento melhor = null;
            BigDecimal menorDiferenca = null;
            for (Lancamento b : banco) {
                BigDecimal diferenca = b.valor().subtract(s.valor()).abs();
                if (b.mesmoSinal(s) && dias(b, s) <= config.toleranciaDias() && diferenca.compareTo(limite) <= 0
                        && (menorDiferenca == null || diferenca.compareTo(menorDiferenca) < 0)) {
                    melhor = b;
                    menorDiferenca = diferenca;
                }
            }
            if (melhor != null) {
                banco.remove(melhor);
                interno.remove(s);
                BigDecimal diferenca = melhor.valor().subtract(s.valor());
                String observacao = String.format("banco %s x sistema %s: diferença de %s (juros, multa ou desconto?)",
                        Formatos.moeda(melhor.valor()), Formatos.moeda(s.valor()), Formatos.moeda(diferenca));
                grupos.add(new Conciliacao(TipoConciliacao.VALOR_DIVERGENTE, List.of(melhor), List.of(s), observacao));
            }
        }
    }

    // ---------------------------------------------------------------- etapa 5

    static String explicarSoNoBanco(Lancamento b) {
        String texto = b.descricao().toUpperCase(Locale.ROOT);
        if (texto.contains("TARIFA") || texto.contains("TAR ") || texto.contains("CESTA")) {
            return "tarifa bancária não lançada no sistema";
        }
        if (texto.contains("IOF") || texto.contains("JUROS")) {
            return "encargo bancário não lançado no sistema";
        }
        if (texto.contains("RENDIMENTO") || texto.contains("REND ")) {
            return "rendimento de aplicação não lançado no sistema";
        }
        if (texto.contains("ESTORNO") || texto.contains("DEVOL")) {
            return "estorno ou devolução: conferir a origem";
        }
        return "sem correspondente no sistema";
    }

    static String explicarSoNoSistema(Lancamento s, Optional<LocalDate> fimDoExtrato) {
        if (fimDoExtrato.isPresent() && s.data().isAfter(fimDoExtrato.get())) {
            return "data depois do fim do extrato: provavelmente ainda não compensado";
        }
        return "não apareceu no extrato: conferir se foi realmente pago ou recebido";
    }

    // ---------------------------------------------------------------- utilitários

    private static long dias(Lancamento a, Lancamento b) {
        return Math.abs(ChronoUnit.DAYS.between(a.data(), b.data()));
    }

    private static LocalDate primeiraData(Conciliacao g) {
        return g.banco().isEmpty() ? g.sistema().get(0).data() : g.banco().get(0).data();
    }
}
