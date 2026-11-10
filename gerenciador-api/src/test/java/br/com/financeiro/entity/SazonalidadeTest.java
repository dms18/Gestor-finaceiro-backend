package br.com.financeiro.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Regras de datas e de reserva de caixa, sem banco. */
class SazonalidadeTest {

    private static Sazonalidade periodo(int mesInicio, int mesFim) {
        Sazonalidade s = new Sazonalidade();
        s.mesInicio = mesInicio;
        s.mesFim = mesFim;
        return s;
    }

    private static Sazonalidade comReserva(int mesInicio, int mesFim, LocalDate dataAlvo) {
        Sazonalidade s = periodo(mesInicio, mesFim);
        s.dataAlvo = dataAlvo;
        s.valorNecessario = new BigDecimal("2000.00");
        s.contaDebito = new Conta();
        return s;
    }

    @Test
    void periodoDentroDoAno() {
        Sazonalidade s = periodo(3, 5);

        assertTrue(s.estaAtivaNoMes(3));
        assertTrue(s.estaAtivaNoMes(4));
        assertTrue(s.estaAtivaNoMes(5));
        assertFalse(s.estaAtivaNoMes(2));
        assertFalse(s.estaAtivaNoMes(6));
    }

    @Test
    void periodoQueViraOAno() {
        Sazonalidade s = periodo(11, 2);

        for (int mes : new int[] {11, 12, 1, 2}) {
            assertTrue(s.estaAtivaNoMes(mes), "mês " + mes);
        }
        for (int mes : new int[] {3, 6, 10}) {
            assertFalse(s.estaAtivaNoMes(mes), "mês " + mes);
        }
    }

    @Test
    void periodoDeUmSoMes() {
        Sazonalidade s = periodo(7, 7);

        assertTrue(s.estaAtivaNoMes(7));
        assertFalse(s.estaAtivaNoMes(6));
        assertFalse(s.estaAtivaNoMes(8));
    }

    @Test
    void temReservaExigeOsTresCampos() {
        assertFalse(periodo(1, 2).temReserva());

        Sazonalidade s = comReserva(1, 2, LocalDate.of(2026, 1, 10));
        assertTrue(s.temReserva());

        s.contaDebito = null;
        assertFalse(s.temReserva());
        s.contaDebito = new Conta();
        s.valorNecessario = null;
        assertFalse(s.temReserva());
        s.valorNecessario = BigDecimal.TEN;
        s.dataAlvo = null;
        assertFalse(s.temReserva());
    }

    @Test
    void fimDoPeriodoNoMesDeFimDoMesmoAno() {
        // data-alvo antes do início do período: vale até o fim do período
        assertEquals(LocalDate.of(2026, 5, 31), comReserva(3, 5, LocalDate.of(2026, 2, 1)).fimDoPeriodo());
        // data-alvo dentro do período
        assertEquals(LocalDate.of(2026, 5, 31), comReserva(3, 5, LocalDate.of(2026, 4, 10)).fimDoPeriodo());
    }

    @Test
    void fimDoPeriodoQueViraOAno() {
        // nov→fev, data em dezembro: termina em fevereiro do ano seguinte
        assertEquals(LocalDate.of(2027, 2, 28), comReserva(11, 2, LocalDate.of(2026, 12, 10)).fimDoPeriodo());
        // mesma coisa com a data já em janeiro
        assertEquals(LocalDate.of(2027, 2, 28), comReserva(11, 2, LocalDate.of(2027, 1, 15)).fimDoPeriodo());
        // data em outubro, antes do início (novembro): ainda é o período que termina em fevereiro seguinte
        assertEquals(LocalDate.of(2027, 2, 28), comReserva(11, 2, LocalDate.of(2026, 10, 20)).fimDoPeriodo());
    }

    @Test
    void fimDoPeriodoRespeitaAnoBissexto() {
        assertEquals(LocalDate.of(2028, 2, 29), comReserva(1, 2, LocalDate.of(2028, 1, 10)).fimDoPeriodo());
        assertEquals(LocalDate.of(2027, 2, 28), comReserva(1, 2, LocalDate.of(2027, 1, 10)).fimDoPeriodo());
    }

    @Test
    void dataDepoisDoFimDoPeriodoNoAnoAssumeAProximaOcorrencia() {
        // período em janeiro, data em abril: a próxima ocorrência termina em janeiro do ano seguinte
        assertEquals(LocalDate.of(2027, 1, 31), comReserva(1, 1, LocalDate.of(2026, 4, 24)).fimDoPeriodo());
    }

    @Test
    void reservaSoApareceDaDataAlvoAteOFimDoPeriodo() {
        Sazonalidade s = comReserva(3, 5, LocalDate.of(2026, 3, 1)); // fim: 31/05/2026

        assertFalse(s.reservaAtiva(LocalDate.of(2026, 2, 28)), "antes da data-alvo");
        assertTrue(s.reservaAtiva(LocalDate.of(2026, 3, 1)), "no dia da data-alvo");
        assertTrue(s.reservaAtiva(LocalDate.of(2026, 4, 15)), "no meio do período");
        assertTrue(s.reservaAtiva(LocalDate.of(2026, 5, 31)), "no último dia");
        assertFalse(s.reservaAtiva(LocalDate.of(2026, 6, 1)), "depois do fim");
    }

    @Test
    void semReservaNuncaFicaAtiva() {
        assertFalse(periodo(1, 12).reservaAtiva(LocalDate.of(2026, 6, 1)));
    }
}
