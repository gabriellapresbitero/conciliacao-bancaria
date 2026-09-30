# 🏦 Conciliação Bancária

[![Testes](https://github.com/gabriellapresbitero/conciliacao-bancaria/actions/workflows/testes.yml/badge.svg)](https://github.com/gabriellapresbitero/conciliacao-bancaria/actions/workflows/testes.yml)
![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-C71A36?logo=apachemaven&logoColor=white)

Ferramenta de linha de comando que cruza o **extrato do banco** (OFX ou CSV) com os
**lançamentos do sistema da empresa** e mostra o que bate, o que não bate e **por quê**.

## O problema

Todo mês, o financeiro de uma empresa precisa responder: *"o que está no meu sistema é o que
realmente passou pela conta?"*. Isso é a **conciliação bancária**, e em pequenas empresas ela
costuma ser feita linha por linha numa planilha, levando horas.

A comparação não é simples porque o banco e o sistema quase nunca registram as coisas do mesmo jeito:

- um boleto pago na sexta só aparece no extrato na segunda;
- três boletos pagos juntos viram **um único débito** "PAGAMENTO EM LOTE";
- as vendas no cartão caem **separadas** no banco, mas o caixa lança o total da semana;
- o aluguel venceu, foi pago com multa, e o valor não bate por R$ 31,20;
- tarifas, IOF e rendimentos aparecem só no banco, porque ninguém lançou no sistema.

## Como funciona

A conciliação roda em etapas, da mais segura para a menos segura. Um lançamento conciliado numa
etapa não é usado de novo nas seguintes.

| Etapa | Regra | Exemplo |
|:---:|---|---|
| 1 | **Exata**: mesmo valor, mesma data | Conta de luz -R$ 842,37 nos dois lados |
| 2 | **Data próxima**: mesmo valor, até 3 dias de diferença (escolhe a data mais próxima) | TED que o sistema registrou um dia antes |
| 3 | **Agrupada**: 2 a 4 lançamentos de um lado **somam exatamente** 1 do outro | 3 boletos = 1 débito em lote |
| 4 | **Divergência de valor**: valores até 5% diferentes, datas próximas | Aluguel pago com multa |
| 5 | **Sobras**: só no banco ou só no sistema, com uma explicação provável | Tarifa, IOF, pagamento ainda não compensado |

A etapa 3 é um problema de **soma de subconjuntos**, resolvido com uma busca recursiva
(*backtracking*) limitada aos candidatos com o mesmo sinal e datas próximas, para não explodir.

No fim, a conciliação **fecha**: a diferença entre o movimento do banco e o do sistema é
exatamente a soma das pendências. Um teste automático garante isso.

### Decisões técnicas

- **Dinheiro em `BigDecimal`, nunca em `double`.** Com `double`, `0.1 + 0.2` dá
  `0.30000000000000004`, e numa conciliação um centavo de diferença já quebra a comparação.
- **Leitor de OFX próprio.** O OFX 1.x (o formato que a maioria dos bancos exporta) não é XML
  válido, porque as tags não são fechadas. Por isso ele é lido com expressões regulares e
  aceita os dois estilos. O OFX costuma vir em Windows-1252, então os acentos são tratados.
- **CSV no padrão brasileiro:** `;` como separador, `1.234,56`, datas `dd/MM/aaaa`, campos entre
  aspas e cabeçalho com ou sem acento, em qualquer ordem.
- **Mensagens de erro úteis:** `sistema.csv, linha 3: data inválida: "31/02/2025"`.
- **Sem dependências** além do JUnit para os testes.

## Como rodar

Pré-requisitos: **Java 21** e **Maven**.

```bash
git clone https://github.com/gabriellapresbitero/conciliacao-bancaria.git
cd conciliacao-bancaria
mvn package

java -jar target/conciliacao.jar \
  --extrato exemplos/extrato_marco.ofx \
  --sistema exemplos/sistema_marco.csv
```

Resultado com os arquivos de exemplo (uma padaria fictícia, março de 2025):

```
========================================================================
CONCILIAÇÃO BANCÁRIA
========================================================================
Conciliado: mesmo valor e mesma data           10 lançamento(s)
Conciliado: mesmo valor, data próxima           2 lançamento(s)
Conciliado: vários lançamentos somam um só      8 lançamento(s)
Divergência de valor                            2 lançamento(s)
Só no extrato do banco                          4 lançamento(s)
Só no sistema                                   2 lançamento(s)

Conciliado automaticamente: 71,4% dos lançamentos
Movimento no banco:   -R$ 6.332,44
Movimento no sistema: -R$ 4.910,47
Diferença:            -R$ 1.421,97

PENDÊNCIAS (explicam a diferença acima)
------------------------------------------------------------------------
14/03/2025  PAGTO BOLETO IMOBILIARIA             -R$ 31,20  banco -R$ 3.531,20 x sistema -R$ 3.500,00: diferença de -R$ 31,20 (juros, multa ou desconto?)
15/03/2025  TARIFA CESTA SERVICOS                -R$ 89,90  tarifa bancária não lançada no sistema
19/03/2025  Recebimento Hotel Mar Azul; ca    -R$ 2.100,00  não apareceu no extrato: conferir se foi realmente pago ou recebido
20/03/2025  RENDIMENTO APLIC AUTOMATICA           R$ 12,34  rendimento de aplicação não lançado no sistema
25/03/2025  PIX RECEBIDO                         R$ 150,00  sem correspondente no sistema
31/03/2025  IOF                                   -R$ 3,21  encargo bancário não lançado no sistema
02/04/2025  Boleto Laticínios Agreste (lei       R$ 640,00  data depois do fim do extrato: provavelmente ainda não compensado
```

Também é gerado o `conciliacao.csv`, com uma linha por lançamento e a coluna `grupo` ligando o
que foi conciliado junto. Ele abre direto no Excel ou no **Power BI**.

### Opções

| Opção | Padrão | Para quê |
|---|---|---|
| `--extrato ARQUIVO` | | Extrato do banco, `.ofx` ou `.csv` |
| `--sistema ARQUIVO` | | Lançamentos do sistema, `.csv` |
| `--saida ARQUIVO` | `conciliacao.csv` | CSV detalhado |
| `--tolerancia-dias N` | 3 | Diferença máxima de datas |
| `--tolerancia-valor P` | 5 | Diferença máxima de valor (%) para apontar divergência |

**Formato do CSV:** colunas `data`, `descricao` e `valor` (obrigatórias) e `documento` (opcional).
Veja [`exemplos/sistema_marco.csv`](exemplos/sistema_marco.csv).

**Código de saída:** `0` = tudo conciliado, `2` = há pendências, `1` = erro. Isso permite usar a
ferramenta em scripts e rotinas automáticas.

## Testes

```bash
mvn test
```

São 38 testes com JUnit. Eles cobrem a leitura de CSV e OFX (formatos de valor e data, aspas,
acentos, mensagens de erro), cada etapa da conciliação, casos de borda (lançamento que não pode
ser usado duas vezes, agrupamento que não mistura entradas com saídas, preferência pela data mais
próxima) e o fechamento da diferença no exemplo completo. O GitHub Actions roda tudo a cada push.

## Estrutura

```
src/main/java/io/github/gabriellapresbitero/conciliacao/
├── Main.java                      # linha de comando
├── Formatos.java                  # R$ 1.234,56 e dd/MM/aaaa
├── modelo/                        # Lancamento, Conciliacao, TipoConciliacao (records e enums)
├── leitura/                       # LeitorCsv, LeitorOfx, Conversor
├── motor/                         # Conciliador (as 5 etapas), Configuracao, ResultadoConciliacao
└── relatorio/                     # RelatorioCsv e Resumo
exemplos/                          # extrato (OFX e CSV) e lançamentos de uma empresa fictícia
```

## Próximos passos

- [ ] Usar a descrição (nome do fornecedor) para desempatar candidatos com o mesmo valor
- [ ] Considerar dias úteis e feriados na tolerância de datas
- [ ] Ler planilhas `.xlsx` direto

---
Feito por **Gabriella Presbítero** · Licença MIT · Os dados de exemplo são fictícios.
