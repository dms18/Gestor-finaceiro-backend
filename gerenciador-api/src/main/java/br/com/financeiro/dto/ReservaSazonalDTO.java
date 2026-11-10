package br.com.financeiro.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Comparação entre o saldo da conta de débito e o caixa necessário de um período sazonal. */
public class ReservaSazonalDTO {
    public Long sazonalidadeId;
    public String nome;
    public String nivelImpacto;
    public LocalDate dataAlvo;
    public LocalDate fimDoPeriodo;
    public BigDecimal valorNecessario;
    public Long contaId;
    public String contaNome;
    /** Saldo atual da conta (saldo inicial + movimentações até hoje). */
    public BigDecimal saldoConta;
    /** saldoConta − valorNecessario: positivo = folga, negativo = falta. */
    public BigDecimal diferenca;
    public boolean suficiente;
    /** Quanto do necessário o saldo cobre, de 0 a 100. */
    public int percentualCobertura;
}
