import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Scanner;

public class Cliente {
    private static final String HOST = "127.0.0.1";
    private static final int PORTA = 12345;
    private static final String CHAVE_CRIPTO = "ENSINARTE2026";

    private static String tokenSessao = null;
    private static String perfilUsuario = null;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);

        System.out.println("====================================================");
        System.out.println("   PORTAL DE CONSULTA ESCOLAR - ESCOLA ENSINARTE    ");
        System.out.println("====================================================");

        // tela de entrada/autenticação
        while (tokenSessao == null) {
            System.out.print("Usuário (ex: aluno, professor, coord): ");
            String usuario = scanner.nextLine().trim();
            System.out.print("Senha: ");
            String senha = scanner.nextLine().trim();

            String resposta = enviarRequisicao("LOGIN|" + usuario + "|" + senha);
            String[] partes = resposta.split("\\|", 3);

            if (partes[0].equals("OK") && partes.length == 3) {
                tokenSessao = partes[1];
                perfilUsuario = partes[2];
                System.out.println("\n[SUCESSO] Bem-vindo(a), " + usuario + "! Perfil ativo: " + perfilUsuario);
            } else {
                System.out.println("[FALHA] " + (partes.length > 1 ? partes[1] : "Erro de conexão.") + "\n");
            }
        }

        // Menu
        boolean executando = true;
        while (executando) {
            exibirMenu();
            System.out.print("Escolha uma opção: ");
            String opcao = scanner.nextLine().trim();

            String comandoServidor = null;

            switch (opcao) {
                case "1":
                    System.out.print("Digite a turma (ex: 1 Ano A, 2 Info): ");
                    comandoServidor = "HORARIO_TURMA|" + tokenSessao + "|" + scanner.nextLine();
                    break;
                case "2":
                    System.out.print("Digite o dia da semana (ex: Segunda, Terca, Quarta): ");
                    comandoServidor = "HORARIO_DIA|" + tokenSessao + "|" + scanner.nextLine();
                    break;
                case "3":
                    System.out.print("Digite o nome do professor (ex: Rafael, Carlos): ");
                    comandoServidor = "HORARIO_PROF|" + tokenSessao + "|" + scanner.nextLine();
                    break;
                case "4":
                    System.out.print("Digite a turma para ver a sala alocada (ex: 2 Info): ");
                    comandoServidor = "SALAS_TURMA|" + tokenSessao + "|" + scanner.nextLine();
                    break;
                case "5":
                    System.out.print("Nome da sala/laboratório (ex: Lab Informatica, Lab Quimica): ");
                    String sala = scanner.nextLine();
                    System.out.print("Dia e horário (ex: Segunda 08:00): ");
                    String diaHora = scanner.nextLine();
                    comandoServidor = "LAB_STATUS|" + tokenSessao + "|" + sala + "|" + diaHora;
                    break;
                case "6":
                    System.out.print("Nome da disciplina (ou deixe vazio para listar todas): ");
                    comandoServidor = "DISCIPLINA|" + tokenSessao + "|" + scanner.nextLine();
                    break;
                case "7":
                    comandoServidor = "AVISOS|" + tokenSessao;
                    break;
                case "0":
                    enviarRequisicao("LOGOUT|" + tokenSessao);
                    System.out.println("Sessão encerrada. Até logo!");
                    executando = false;
                    break;
                default:
                    System.out.println("[AVISO] Opção inválida. Tente novamente.");
            }

            if (comandoServidor != null) {
                String resposta = enviarRequisicao(comandoServidor);
                exibirRespostaFormatada(resposta);
            }
        }
        scanner.close();
    }

    private static void exibirMenu() {
        System.out.println("\n------------------- MENU DE CONSULTAS -------------------");
        System.out.println("1 - Horários de aula por Turma");
        System.out.println("2 - Horários de aula por Dia da Semana");
        System.out.println("3 - Horários por Professor [Restrito: Professor/Coord]");
        System.out.println("4 - Consultar Sala de uma Turma");
        System.out.println("5 - Verificar Disponibilidade de Sala/Laboratório");
        System.out.println("6 - Informações de Cursos e Disciplinas");
        System.out.println("7 - Calendário Escolar e Avisos");
        System.out.println("0 - Sair (Logout)");
        System.out.println("---------------------------------------------------------");
    }

    private static void exibirRespostaFormatada(String resposta) {
        String[] partes = resposta.split("\\|", 2);
        System.out.println("\n>>> RESULTADO DA CONSULTA <<<");
        if (partes[0].equals("OK")) {
            System.out.println(partes.length > 1 ? partes[1] : "Operação concluída.");
        } else {
            System.out.println("[ATENÇÃO] " + (partes.length > 1 ? partes[1] : "Erro desconhecido."));
        }
        System.out.println("---------------------------------------------------------");
    }

    private static String enviarRequisicao(String mensagem) {
        try (Socket socket = new Socket(HOST, PORTA);
             PrintWriter saida = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
             BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            saida.println(criptografar(mensagem));
            String respostaCriptografada = entrada.readLine();
            if (respostaCriptografada != null) {
                return descriptografar(respostaCriptografada);
            }
            return "ERRO|Servidor não enviou resposta.";

        } catch (IOException e) {
            return "ERRO|Não foi possível conectar ao servidor (" + e.getMessage() + ")";
        }
    }

    private static String criptografar(String texto) {
        byte[] dados = texto.getBytes(StandardCharsets.UTF_8);
        byte[] chave = CHAVE_CRIPTO.getBytes(StandardCharsets.UTF_8);
        byte[] res = new byte[dados.length];
        for (int i = 0; i < dados.length; i++) {
            res[i] = (byte) (dados[i] ^ chave[i % chave.length]);
        }
        return Base64.getEncoder().encodeToString(res);
    }

    private static String descriptografar(String textoBase64) {
        byte[] dados = Base64.getDecoder().decode(textoBase64);
        byte[] chave = CHAVE_CRIPTO.getBytes(StandardCharsets.UTF_8);
        byte[] res = new byte[dados.length];
        for (int i = 0; i < dados.length; i++) {
            res[i] = (byte) (dados[i] ^ chave[i % chave.length]);
        }
        return new String(res, StandardCharsets.UTF_8);
    }
}