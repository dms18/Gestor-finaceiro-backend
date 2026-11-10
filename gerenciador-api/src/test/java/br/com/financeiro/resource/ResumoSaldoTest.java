package br.com.financeiro.resource;

import static br.com.financeiro.support.ApiTestSupport.como;
import static br.com.financeiro.support.ApiTestSupport.criarConta;
import static br.com.financeiro.support.ApiTestSupport.criarTransacao;
import static br.com.financeiro.support.ApiTestSupport.novoUsuario;
import static br.com.financeiro.support.ApiTestSupport.numero;
import static br.com.financeiro.support.ApiTestSupport.resumo;
import static br.com.financeiro.support.ApiTestSupport.transacao;
import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.financeiro.support.ApiTestSupport.Sessao;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Regras de saldo do Dashboard e da tela de Contas:
 * saldo = saldo inicial das contas + movimentações até hoje (lançamentos futuros não contam);
 * lançamentos sem conta entram no saldo e são sinalizados em "semConta".
 */
@QuarkusTest
class ResumoSaldoTest {

    private static final LocalDate HOJE = LocalDate.now();
    private static final double DELTA = 0.001;

    private static void assertNum(double esperado, Response r, String caminho) {
        assertEquals(esperado, numero(r, caminho), DELTA, caminho);
    }

    @Test
    void saldoInicialDasContasEntraNoSaldo() {
        Sessao s = novoUsuario();
        criarConta(s, "A", "PF", 1000);
        criarConta(s, "B", "PJ", 500.50);

        Response r = resumo(s, "");
        assertNum(1500.50, r, "saldo");
        assertNum(0, r, "totalReceitas");
        assertNum(0, r, "totalDespesas");
    }

    @Test
    void movimentacoesAteHojeSomamEDescontam() {
        Sessao s = novoUsuario();
        long conta = criarConta(s, "Principal", "PF", 1000);
        criarTransacao(s, "RECEITA", 200, HOJE, conta);
        criarTransacao(s, "DESPESA", 50, HOJE.minusDays(3), conta);

        Response r = resumo(s, "");
        assertNum(1150, r, "saldo");
        assertNum(1150, r, "porConta[0].saldoAtual");
        assertNum(1000, r, "porConta[0].saldo"); // saldo inicial continua disponível
    }

    @Test
    void lancamentosFuturosNaoContamNoSaldoAtual() {
        Sessao s = novoUsuario();
        long conta = criarConta(s, "Principal", "PF", 1000);
        criarTransacao(s, "RECEITA", 300, HOJE.plusDays(1), conta);
        criarTransacao(s, "DESPESA", 80, HOJE.plusMonths(2), conta);

        Response r = resumo(s, "");
        assertNum(1000, r, "saldo");
        assertNum(1000, r, "porConta[0].saldoAtual");
    }

    @Test
    void lancamentoDeHojeConta_deAmanhaNao() {
        Sessao s = novoUsuario();
        long conta = criarConta(s, "Principal", "PF", 0);
        criarTransacao(s, "RECEITA", 100, HOJE, conta);
        criarTransacao(s, "RECEITA", 999, HOJE.plusDays(1), conta);

        assertNum(100, resumo(s, ""), "saldo");
    }

    @Test
    void lancamentosSemContaEntramNoSaldoESaoSinalizados() {
        Sessao s = novoUsuario();
        long conta = criarConta(s, "Principal", "PF", 1000);
        criarTransacao(s, "RECEITA", 200, HOJE, conta);
        criarTransacao(s, "DESPESA", 100, HOJE, null);

        Response r = resumo(s, "");
        assertNum(1100, r, "saldo");                  // 1000 + 200 - 100
        assertNum(1200, r, "porConta[0].saldoAtual"); // a conta não recebe o lançamento sem conta
        assertNum(-100, r, "semConta");
        assertEquals(1, r.jsonPath().getInt("semContaQtd"));
    }

    @Test
    void saldoEIgualASomaDasContasMaisOsLancamentosSemConta() {
        Sessao s = novoUsuario();
        long a = criarConta(s, "A", "PF", 100);
        long b = criarConta(s, "B", "PF", 200);
        criarTransacao(s, "RECEITA", 50, HOJE, a);
        criarTransacao(s, "DESPESA", 30, HOJE, b);
        criarTransacao(s, "DESPESA", 20, HOJE, null);

        Response r = resumo(s, "");
        double somaContas = numero(r, "porConta[0].saldoAtual") + numero(r, "porConta[1].saldoAtual");
        assertEquals(numero(r, "saldo"), somaContas + numero(r, "semConta"), DELTA);
    }

    @Test
    void semLancamentosSemContaNaoHaAviso() {
        Sessao s = novoUsuario();
        long conta = criarConta(s, "Principal", "PF", 10);
        criarTransacao(s, "RECEITA", 5, HOJE, conta);

        Response r = resumo(s, "");
        assertNum(0, r, "semConta");
        assertEquals(0, r.jsonPath().getInt("semContaQtd"));
    }

    @Test
    void totaisDeReceitasEDespesasSaoDoPeriodoPedido() {
        Sessao s = novoUsuario();
        long conta = criarConta(s, "Principal", "PF", 1000);
        criarTransacao(s, "RECEITA", 200, HOJE, conta);
        criarTransacao(s, "DESPESA", 50, HOJE, conta);
        criarTransacao(s, "RECEITA", 700, LocalDate.of(2020, 3, 10), conta); // outro mês

        Response mesAtual = resumo(s, "?mes=" + HOJE.getMonthValue() + "&ano=" + HOJE.getYear());
        assertNum(200, mesAtual, "totalReceitas");
        assertNum(50, mesAtual, "totalDespesas");

        Response tudo = resumo(s, "");
        assertNum(900, tudo, "totalReceitas");
    }

    @Test
    void saldoDeUmMesPassadoEAcumuladoAteOFimDaqueleMes() {
        Sessao s = novoUsuario();
        long conta = criarConta(s, "Principal", "PF", 1000);
        criarTransacao(s, "RECEITA", 700, LocalDate.of(2020, 3, 10), conta);
        criarTransacao(s, "DESPESA", 100, LocalDate.of(2020, 4, 5), conta);
        criarTransacao(s, "RECEITA", 50, HOJE, conta);

        assertNum(1000, resumo(s, "?mes=2&ano=2020"), "saldo");   // antes de qualquer lançamento
        assertNum(1700, resumo(s, "?mes=3&ano=2020"), "saldo");   // + 700
        assertNum(1600, resumo(s, "?mes=4&ano=2020"), "saldo");   // - 100
        assertNum(1650, resumo(s, ""), "saldo");                  // hoje: inclui os 50 de hoje
    }

    @Test
    void filtroPorPessoaConsideraSoContasELancamentosDaquelePerfil() {
        Sessao s = novoUsuario();
        long pf = criarConta(s, "Pessoal", "PF", 1000);
        long pj = criarConta(s, "Empresa", "PJ", 5000);
        criarTransacao(s, "RECEITA", 100, HOJE, pf);
        como(s).body(transacao("Venda PJ", 400, HOJE, "RECEITA", "PJ", pj)).post("/transacoes").then().statusCode(201);

        assertNum(1100, resumo(s, "?tipoPessoa=PF"), "saldo");
        assertNum(5400, resumo(s, "?tipoPessoa=PJ"), "saldo");
        assertNum(6500, resumo(s, ""), "saldo");
    }

    @Test
    void parcelasFuturasNaoReduzemOSaldoAtual() {
        Sessao s = novoUsuario();
        long conta = criarConta(s, "Cartão", "PF", 1000);
        Map<String, Object> compra = transacao("Notebook", 600, HOJE, "DESPESA", "PF", conta);
        compra.put("parcelado", true);
        compra.put("numeroParcelas", 3);
        como(s).body(compra).post("/transacoes").then().statusCode(201);

        // só a 1ª parcela (hoje, R$ 200) já aconteceu; as outras 2 são futuras
        assertNum(800, resumo(s, ""), "saldo");
        assertNum(800, resumo(s, ""), "porConta[0].saldoAtual");
    }

    @Test
    void cadaUsuarioVeSoOProprioResumo() {
        Sessao a = novoUsuario();
        Sessao b = novoUsuario();
        criarConta(a, "Da Ana", "PF", 777);

        assertNum(0, resumo(b, ""), "saldo");
        assertNum(777, resumo(a, ""), "saldo");
    }
}
