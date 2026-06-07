package br.com.financeiro.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ResumoFinanceiroDTO {
    public BigDecimal totalReceitas;
    public BigDecimal totalDespesas;
    public BigDecimal saldo;
    public List<ResumoPorConta> porConta;

    public ResumoFinanceiroDTO() {
        this.totalReceitas = BigDecimal.ZERO;
        this.totalDespesas = BigDecimal.ZERO;
        this.saldo = BigDecimal.ZERO;
        this.porConta = new ArrayList<>();
    }

    public static class ResumoPorConta {
        public Long contaId;
        public String contaNome;
        public BigDecimal saldo;
        public BigDecimal receitas;
        public BigDecimal despesas;

        public ResumoPorConta(Long contaId, String contaNome, BigDecimal saldo, BigDecimal receitas, BigDecimal despesas) {
            this.contaId = contaId;
            this.contaNome = contaNome;
            this.saldo = saldo;
            this.receitas = receitas;
            this.despesas = despesas;
        }
    }
}
