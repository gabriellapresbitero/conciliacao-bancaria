package io.github.gabriellapresbitero.conciliacao.relatorio;

import io.github.gabriellapresbitero.conciliacao.Formatos;
import io.github.gabriellapresbitero.conciliacao.modelo.Conciliacao;
import io.github.gabriellapresbitero.conciliacao.modelo.Lancamento;
import io.github.gabriellapresbitero.conciliacao.motor.ResultadoConciliacao;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Gera um CSV com uma linha por lançamento, pronto para abrir no Excel ou no Power BI.
 * A coluna "grupo" liga os lançamentos que foram conciliados entre si.
 */
public class RelatorioCsv {

    static final String CABECALHO = "grupo;situacao;conciliado;origem;id;data;descricao;valor;observacao";

    public List<String> linhas(ResultadoConciliacao resultado) {
        List<String> linhas = new ArrayList<>();
        linhas.add(CABECALHO);
        int numero = 0;
        for (Conciliacao grupo : resultado.grupos()) {
            numero++;
            List<Lancamento> todos = new ArrayList<>(grupo.banco());
            todos.addAll(grupo.sistema());
            for (Lancamento l : todos) {
                linhas.add(String.join(";",
                        String.valueOf(numero),
                        grupo.tipo().descricao(),
                        grupo.tipo().conciliado() ? "sim" : "não",
                        l.origem().name(),
                        texto(l.id()),
                        l.data().format(Formatos.DATA),
                        texto(l.descricao()),
                        Formatos.numeroCsv(l.valor()),
                        texto(grupo.observacao())));
            }
        }
        return linhas;
    }

    public void salvar(ResultadoConciliacao resultado, Path destino) throws IOException {
        if (destino.getParent() != null) {
            Files.createDirectories(destino.getParent());
        }
        // O "﻿" (BOM) faz o Excel reconhecer o arquivo como UTF-8 e mostrar os acentos certos.
        Files.writeString(destino, "﻿" + String.join("\r\n", linhas(resultado)) + "\r\n", StandardCharsets.UTF_8);
    }

    /** Coloca o texto entre aspas se ele tiver ";" ou aspas, seguindo o padrão CSV. */
    static String texto(String valor) {
        if (valor.contains(";") || valor.contains("\"")) {
            return "\"" + valor.replace("\"", "\"\"") + "\"";
        }
        return valor;
    }
}
