package br.com.financeiro.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Indicadores agregados para o painel do administrador. Não traz valores financeiros dos usuários. */
public class AdminResumoDTO {

    public LocalDateTime geradoEm = LocalDateTime.now();

    public Usuarios usuarios = new Usuarios();
    public Atividade atividade = new Atividade();
    /** Cadastros por dia nos últimos 30 dias (data yyyy-MM-dd), em ordem cronológica. */
    public List<CadastroDia> cadastrosPorDia;
    /** Cadastros mais recentes (dados cadastrais e contagem de lançamentos; nada de saldos ou valores). */
    public List<UsuarioRecente> usuariosRecentes;

    public static class Usuarios {
        public long total;
        public long ativos;
        public long inativos;
        public long novos7Dias;
        public long novos30Dias;
        public long aceitaramTermos;
        public Map<String, Long> porPerfil;
    }

    public static class Atividade {
        public long transacoes;
        public long transacoes30Dias;
        public long contas;
        public long categorias;
        public long sazonalidades;
        public long investimentos;
        public Map<String, Long> investimentosPorStatus;
        /** Usuários distintos que lançaram transações nos últimos 30 dias. */
        public long usuariosAtivos30Dias;
    }

    public static class CadastroDia {
        public String data;
        public long quantidade;

        public CadastroDia(String data, long quantidade) {
            this.data = data;
            this.quantidade = quantidade;
        }
    }

    public static class UsuarioRecente {
        public Long id;
        public String nome;
        public String email;
        public String perfil;
        public boolean ativo;
        public boolean admin;
        public LocalDateTime criadoEm;
        public long transacoes;
    }
}
