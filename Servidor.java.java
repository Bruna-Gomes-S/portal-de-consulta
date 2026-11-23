import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Servidor {
    private static final int PORTA = 12345;
    private static final String CHAVE_CRIPTO = "ENSINARTE2026";
    private static final Map<String, Sessao> sessoesAtivas = new ConcurrentHashMap<>();

    static class Sessao {
        String usuario;
        String perfil;
        Sessao(String usuario, String perfil) {
            this.usuario = usuario;
            this.perfil = perfil;
        }
    }

    public static void main(String[] args) {
        inicializarArquivosCSV();
        System.out.println("[SERVIDOR] Base de dados CSV e segurança inicializadas.");
        System.out.println("[SERVIDOR] Escutando na porta " + PORTA + "...");

        try (ServerSocket serverSocket = new ServerSocket(PORTA)) {
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(() -> atenderCliente(socket)).start();
            }
        } catch (IOException e) {
            System.err.println("[SERVIDOR] Erro crítico: " + e.getMessage());
        }
    }

    private static void atenderCliente(Socket socket) {
        String ipCliente = socket.getInetAddress().getHostAddress();
        try (
            BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter saida = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)
        ) {
            String pacoteCriptografado = entrada.readLine();
            if (pacoteCriptografado == null || pacoteCriptografado.trim().isEmpty()) {
                return;
            }

            String requisicao = descriptografar(pacoteCriptografado);
            String resposta = processarRequisicao(requisicao, ipCliente);
            saida.println(criptografar(resposta));

        } catch (Exception e) {
            registrarLog(ipCliente, "DESCONHECIDO", "ERRO_COMUNICACAO: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private static String processarRequisicao(String req, String ip) {
        // Remove quebras de linha e caracteres que bagunçam CSV ou logs
        req = req.replace("\r", "").replace("\n", "").trim();
        String[] partes = req.split("\\|", -1);

        if (partes.length < 2) {
            registrarLog(ip, "ANONIMO", "REQUISICAO_INVALIDA");
            return "ERRO|Formato de requisição inválido.";
        }

        String comando = partes[0].toUpperCase();

        // Fluxo de Login
        if (comando.equals("LOGIN")) {
            if (partes.length < 3 || partes[1].isEmpty() || partes[2].isEmpty()) {
                return "ERRO|Usuário e senha são obrigatórios.";
            }
            String user = sanitizar(partes[1]);
            String senhaHash = gerarSHA256(partes[2]);
            String perfil = autenticarUsuario(user, senhaHash);

            if (perfil != null) {
                String token = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                sessoesAtivas.put(token, new Sessao(user, perfil));
                registrarLog(ip, user, "LOGIN_SUCESSO (Perfil: " + perfil + ")");
                return "OK|" + token + "|" + perfil;
            } else {
                registrarLog(ip, user, "LOGIN_FALHA");
                return "ERRO|Usuário ou senha incorretos.";
            }
        }

        // validação de token para demais comandos
        String token = partes[1];
        Sessao sessao = sessoesAtivas.get(token);
        if (sessao == null) {
            registrarLog(ip, "TOKEN_INVALIDO", "TENTATIVA_ACESSO_NAO_AUTORIZADO");
            return "ERRO|Sessão inválida ou expirada. Faça login novamente.";
        }

        String filtro = partes.length > 2 ? sanitizar(partes[2]) : "";
        String subFiltro = partes.length > 3 ? sanitizar(partes[3]) : "";

        switch (comando) {
            case "HORARIO_TURMA":
                registrarLog(ip, sessao.usuario, "CONSULTA_HORARIO_TURMA: " + filtro);
                return consultarHorarios(0, filtro);

            case "HORARIO_DIA":
                registrarLog(ip, sessao.usuario, "CONSULTA_HORARIO_DIA: " + filtro);
                return consultarHorarios(1, filtro);

            case "HORARIO_PROF":
                // Controle de Acesso (RBAC): Apenas PROFESSOR ou COORD
                if (sessao.perfil.equals("ALUNO")) {
                    registrarLog(ip, sessao.usuario, "ACESSO_NEGADO_HORARIO_PROF");
                    return "ERRO|Acesso negado. Apenas professores e coordenação podem consultar grade por docente.";
                }
                registrarLog(ip, sessao.usuario, "CONSULTA_HORARIO_PROF: " + filtro);
                return consultarHorarios(5, filtro);

            case "SALAS_TURMA":
                registrarLog(ip, sessao.usuario, "CONSULTA_SALA_TURMA: " + filtro);
                return consultarHorarios(0, filtro);

            case "LAB_STATUS":
                registrarLog(ip, sessao.usuario, "CONSULTA_LAB_STATUS: " + filtro + " @ " + subFiltro);
                return verificarDisponibilidadeSala(filtro, subFiltro);

            case "DISCIPLINA":
                registrarLog(ip, sessao.usuario, "CONSULTA_DISCIPLINA: " + filtro);
                return consultarDisciplinas(filtro);

            case "AVISOS":
                registrarLog(ip, sessao.usuario, "CONSULTA_AVISOS (Perfil: " + sessao.perfil + ")");
                return consultarAvisos(sessao.perfil);

            case "LOGOUT":
                sessoesAtivas.remove(token);
                registrarLog(ip, sessao.usuario, "LOGOUT");
                return "OK|Sessão encerrada com segurança.";

            default:
                registrarLog(ip, sessao.usuario, "COMANDO_DESCONHECIDO: " + comando);
                return "ERRO|Comando não reconhecido pelo servidor.";
        }
    }

    // BUSCA DE DADOS NOS ARQUIVOS CS

    private static String consultarHorarios(int colunaIndex, String termo) {
        if (termo.isEmpty()) return "ERRO|Informe um termo para pesquisa.";
        StringBuilder sb = new StringBuilder();
        try {
            List<String> linhas = Files.readAllLines(Paths.get("horarios.csv"), StandardCharsets.UTF_8);
            for (int i = 1; i < linhas.size(); i++) {
                String[] col = linhas.get(i).split(";");
                if (col.length >= 6 && col[colunaIndex].toLowerCase().contains(termo.toLowerCase())) {
                    sb.append(String.format("• Turma: %s | Dia: %s | Horário: %s | Disc.: %s | Sala: %s | Prof.: %s\n",
                            col[0], col[1], col[2], col[3], col[4], col[5]));
                }
            }
        } catch (IOException e) {
            return "ERRO|Falha ao ler arquivo de horários.";
        }
        return sb.length() > 0 ? "OK|\n" + sb.toString() : "OK|Nenhum horário encontrado para '" + termo + "'.";
    }

    private static String verificarDisponibilidadeSala(String sala, String diaHorario) {
        if (sala.isEmpty() || diaHorario.isEmpty()) {
            return "ERRO|Informe o nome da sala/laboratório e o dia/horário (Ex: Lab Informatica | Segunda 08:00).";
        }
        try {
            List<String> linhas = Files.readAllLines(Paths.get("horarios.csv"), StandardCharsets.UTF_8);
            for (int i = 1; i < linhas.size(); i++) {
                String[] col = linhas.get(i).split(";");
                if (col.length >= 6) {
                    boolean mesmaSala = col[4].toLowerCase().contains(sala.toLowerCase());
                    String diaHoraLinha = (col[1] + " " + col[2]).toLowerCase();
                    if (mesmaSala && diaHoraLinha.contains(diaHorario.toLowerCase())) {
                        return String.format("OK|STATUS: OCUPADA -> %s está em uso pela turma %s (%s - %s).",
                                col[4], col[0], col[3], col[5]);
                    }
                }
            }
            return "OK|STATUS: LIVRE -> O espaço '" + sala + "' está disponível em '" + diaHorario + "'.";
        } catch (IOException e) {
            return "ERRO|Falha ao verificar disponibilidade de salas.";
        }
    }

    private static String consultarDisciplinas(String termo) {
        StringBuilder sb = new StringBuilder();
        try {
            List<String> linhas = Files.readAllLines(Paths.get("disciplinas.csv"), StandardCharsets.UTF_8);
            for (int i = 1; i < linhas.size(); i++) {
                String[] col = linhas.get(i).split(";");
                if (col.length >= 5 && (termo.isEmpty() || col[0].toLowerCase().contains(termo.toLowerCase()))) {
                    sb.append(String.format("• %s (%s) | Prof.: %s\n  Ementa: %s | Pré-req.: %s\n",
                            col[0], col[1], col[2], col[3], col[4]));
                }
            }
        } catch (IOException e) {
            return "ERRO|Falha ao ler arquivo de disciplinas.";
        }
        return sb.length() > 0 ? "OK|\n" + sb.toString() : "OK|Nenhuma disciplina encontrada.";
    }

    private static String consultarAvisos(String perfilUsuario) {
        StringBuilder sb = new StringBuilder();
        try {
            List<String> linhas = Files.readAllLines(Paths.get("avisos.csv"), StandardCharsets.UTF_8);
            for (int i = 1; i < linhas.size(); i++) {
                String[] col = linhas.get(i).split(";");
                if (col.length >= 4) {
                    String publico = col[3].trim().toUpperCase();
                    // Aluno só vê avisos GERAL; Professor/Coord vê GERAL e INTERNO
                    if (publico.equals("GERAL") || !perfilUsuario.equals("ALUNO")) {
                        sb.append(String.format("[%s] (%s) %s: %s\n", col[0], publico, col[1], col[2]));
                    }
                }
            }
        } catch (IOException e) {
            return "ERRO|Falha ao ler arquivo de avisos.";
        }
        return sb.length() > 0 ? "OK|\n" + sb.toString() : "OK|Nenhum aviso disponível.";
    }

    private static String autenticarUsuario(String usuario, String senhaHash) {
        try {
            List<String> linhas = Files.readAllLines(Paths.get("usuarios.csv"), StandardCharsets.UTF_8);
            for (int i = 1; i < linhas.size(); i++) {
                String[] col = linhas.get(i).split(";");
                if (col.length >= 3 && col[0].equalsIgnoreCase(usuario) && col[1].equals(senhaHash)) {
                    return col[2].trim().toUpperCase();
                }
            }
        } catch (IOException e) {
            System.err.println("Erro ao ler usuarios.csv");
        }
        return null;
    }

    // --- SEGURANÇA, LOGS E INICIALIZAÇÃO DE ARQUIVOS ---

    private static String sanitizar(String texto) {
        return texto.replaceAll("[;|\r\n]", "").trim();
    }

    private static String gerarSHA256(String senha) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(senha.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return "";
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

    private static synchronized void registrarLog(String ip, String usuario, String acao) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String linhaLog = String.format("[%s] IP: %s | Usuario: %s | Acao: %s%n", timestamp, ip, usuario, acao);
        try {
            Files.write(Paths.get("log.txt"), linhaLog.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Erro ao gravar log: " + e.getMessage());
        }
    }

    private static void inicializarArquivosCSV() {
        try {
            if (!Files.exists(Paths.get("usuarios.csv"))) {
                // Senhas padrão: aluno123, prof123, coord123 
                List<String> users = Arrays.asList(
                    "usuario;senha_hash;perfil",
                    "aluno;" + gerarSHA256("aluno123") + ";ALUNO",
                    "professor;" + gerarSHA256("prof123") + ";PROFESSOR",
                    "coord;" + gerarSHA256("coord123") + ";COORD"
                );
                Files.write(Paths.get("usuarios.csv"), users, StandardCharsets.UTF_8);
            }

            if (!Files.exists(Paths.get("horarios.csv"))) {
                List<String> horarios = Arrays.asList(
                    "turma;dia;horario;disciplina;sala;professor",
                    "1 Ano A;Segunda;08:00;Matematica;Sala 101;Prof. Carlos",
                    "1 Ano A;Terca;10:00;Quimica;Lab Quimica;Profa. Helena",
                    "2 Info;Segunda;08:00;Programacao Java;Lab Informatica;Prof. Rafael",
                    "2 Info;Quarta;14:00;Redes de Computadores;Lab Informatica;Prof. Rafael",
                    "3 Ano B;Quinta;09:00;Literatura;Biblioteca;Profa. Ana"
                );
                Files.write(Paths.get("horarios.csv"), horarios, StandardCharsets.UTF_8);
            }

            if (!Files.exists(Paths.get("disciplinas.csv"))) {
                List<String> disciplinas = Arrays.asList(
                    "nome;carga_horaria;professor;ementa;prerequisitos",
                    "Programacao Java;80h;Prof. Rafael;Orientacao a objetos, sockets e tratamento de excecoes;Logica de Programacao",
                    "Redes de Computadores;60h;Prof. Rafael;Arquitetura TCP/IP, protocolos e seguranca basica;Nenhum",
                    "Quimica Geral;60h;Profa. Helena;Tabela periodica, ligacoes quimicas e praticas laboratoriais;Nenhum",
                    "Matematica;120h;Prof. Carlos;Funcoes, geometria analitica e estatistica basica;Nenhum"
                );
                Files.write(Paths.get("disciplinas.csv"), disciplinas, StandardCharsets.UTF_8);
            }

            if (!Files.exists(Paths.get("avisos.csv"))) {
                List<String> avisos = Arrays.asList(
                    "data;titulo;descricao;publico",
                    "01/12/2026;Semana de Provas;Avaliacoes finais do 4 bimestre ocorrem de 01 a 05/12.;GERAL",
                    "07/12/2026;Entrega Projeto Integrador;Prazo limite para submissao no ambiente virtual.;GERAL",
                    "10/12/2026;Conselho de Classe;Reuniao obrigatoria dos docentes na Sala dos Professores as 14h.;INTERNO",
                    "15/12/2026;Recesso Escolar;Inicio do recesso de fim de ano da Escola Ensinarte.;GERAL"
                );
                Files.write(Paths.get("avisos.csv"), avisos, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            System.err.println("Erro ao criar arquivos CSV iniciais: " + e.getMessage());
        }
    }
}
