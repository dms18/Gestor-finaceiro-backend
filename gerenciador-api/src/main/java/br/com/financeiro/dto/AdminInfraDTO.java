package br.com.financeiro.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Uso do servidor (VPS) e de cada container, para o painel do administrador. Campos nulos = não foi
 * possível medir (por exemplo, fora de Linux ou sem acesso às estatísticas do Docker).
 */
public class AdminInfraDTO {

    public LocalDateTime geradoEm = LocalDateTime.now();
    public Vps vps = new Vps();
    /** false quando a API não tem como consultar o Docker (DOCKER_STATS_URL vazio ou fora do ar). */
    public boolean containersDisponiveis;
    public String containersErro;
    public List<Conteiner> containers = new ArrayList<>();

    public static class Vps {
        public Integer cpus;
        /** Uso de CPU do servidor (0 a 100), medido numa janela curta. */
        public Double cpuPercentual;
        public Double load1;
        public Double load5;
        public Double load15;
        public Long memoriaTotalBytes;
        public Long memoriaUsadaBytes;
        public Long swapTotalBytes;
        public Long swapUsadaBytes;
        public Long discoTotalBytes;
        public Long discoUsadoBytes;
        public Long uptimeSegundos;
    }

    public static class Conteiner {
        public String id;
        /** Nome do serviço no compose (ex.: api, db, web) ou o nome do container. */
        public String nome;
        public String imagem;
        public String estado;
        /** Texto do Docker, ex.: "Up 5 hours (healthy)". */
        public String situacao;
        public Double cpuPercentual;
        public Long memoriaUsadaBytes;
        public Long memoriaLimiteBytes;
        public Long redeRxBytes;
        public Long redeTxBytes;
        public Long discoLeituraBytes;
        public Long discoEscritaBytes;
    }
}
