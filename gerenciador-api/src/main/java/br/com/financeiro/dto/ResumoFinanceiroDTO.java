package br.com.financeiro.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ResumoFinanceiroDTO {
    public BigDecimal totalReceitas;
    public BigDecimal totalDespesas;
    public BigDecimal saldo;
    public List<ResumoPorConta> porConta;
    /** Resultado (receitas − despesas) dos lançamentos sem conta vinculada, já incluído em {@code saldo}. */
    public BigDecimal semConta;
    /** Quantidade de lançamentos sem conta vinculada considerados no saldo. */
    public int semContaQtd;

    public ResumoFinanceiroDTO() {
        this.totalReceitas = BigDecimal.ZERO;
        this.totalDespesas = BigDecimal.ZERO;
        this.saldo = BigDecimal.ZERO;
        this.porConta = new ArrayList<>();
        this.semConta = BigDecimal.ZERO;
        this.semContaQtd = 0;
    }

    public static class ResumoPorConta {
        public Long contaId;
        public String contaNome;
        /** Saldo inicial cadastrado na conta. */
        public BigDecimal saldo;
        /** Receitas/despesas lançadas na conta dentro do período pedido. */
        public BigDecimal receitas;
        public BigDecimal despesas;
        /** Saldo inicial + movimentações da conta até hoje (ou até o fim do período, se anterior). */
        public BigDecimal saldoAtual;

        public ResumoPorConta(Long contaId, String contaNome, BigDecimal saldo, BigDecimal receitas,
                              BigDecimal despesas, BigDecimal saldoAtual) {
            this.contaId = contaId;
            this.contaNome = contaNome;
            this.saldo = saldo;
            this.receitas = receitas;
            this.despesas = despesas;
            this.saldoAtual = saldoAtual;
        }
    }
}
