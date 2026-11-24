# Portal de Consulta (Cliente-Servidor)

Este repositório contém o código-fonte e os arquivos de persistência do sistema `portal-de-consulta`. O projeto foi desenvolvido em **Java** na **Eclipse IDE**, aplicando arquitetura cliente-servidor sobre **Sockets TCP/IP**, segurança de dados e controle de acesso baseado em papéis para gerenciamento e consulta de informações em rede.

---

## 🎯 Visão Geral e Funcionalidades

O sistema opera como uma aplicação distribuída de console que centraliza a leitura e a consulta de dados estruturados, garantindo confidencialidade no tráfego de rede e restrição de comandos por perfil de usuário.

* **Comunicação em Rede:** Conexão simultânea entre múltiplos clientes e o servidor central utilizando **Sockets TCP/IP** na porta `12345`.
* **Segurança e Criptografia:** Proteção das mensagens trafegadas com codificação **XOR/Base64** e armazenamento seguro de credenciais por meio de hash **SHA-256**.
* **Controle de Acesso (RBAC):** Autenticação de usuários e liberação de funcionalidades de acordo com o nível de permissão vinculado ao perfil.

---

## 🛠️ Arquitetura do Software e Estrutura de Arquivos

Para garantir a separação de responsabilidades entre a interface de interação, o processamento em rede e o armazenamento, o projeto é composto pelos seguintes componentes:

1. `Servidor.java`: Núcleo de processamento e gerenciamento de conexões. Escuta requisições na porta `12345`, valida hashes de autenticação, aplica as regras de permissão (RBAC), realiza a leitura das bases de dados e devolve as respostas criptografadas ao cliente.
2. `Cliente.java`: Ponto de entrada da interface de console do usuário. Captura as credenciais e comandos digitados no terminal, criptografa os pacotes enviados ao servidor e exibe os resultados das consultas na tela.
3. **Bases de Dados Locais (`.csv`)**: Arquivos de texto estruturado que atuam como camada de persistência leve no servidor:
   * `usuarios.csv`: Armazena os identificadores, perfis de permissão e hashes de senha dos usuários.
   * `disciplinas.csv`: Contém o catálogo de registros cadastrados para consulta.
   * `horarios.csv`: Mantém a grade de horários disponível no sistema.
   * `avisos.csv`: Centraliza os comunicados e notificações registrados.

---

## 🔒 Camadas de Segurança Implementadas

O fluxo de comunicação foi projetado para evitar a exposição de dados em texto claro durante a transmissão ou armazenamento:

* **Integridade de Credenciais:** As senhas não são salvas em formato legível; o servidor compara o resumo criptográfico gerado via **SHA-256** no momento do login.
* **Ofuscação de Tráfego:** Todo payload trocado entre `Cliente.java` e `Servidor.java` passa por cifragem **XOR** combinada com codificação **Base64** antes de trafegar pelo socket.

---

## 🚀 Como Executar o Projeto Localmente

Certifique-se de ter o **Java Development Kit (JDK 8 ou superior)** instalado e configurado no seu ambiente.

### 1. Compilação dos Arquivos
Abra o terminal na pasta onde estão localizados os arquivos `.java` e `.csv` e execute os comandos abaixo para gerar os bytecodes:

```bash
javac Servidor.java
javac Cliente.java
```
### 2. Inicialização do Servidor
Em um primeiro terminal, inicie o servidor para carregar os arquivos .csv e abrir a porta 12345:

```Bash
java Servidor
```
### 3. Conexão do Cliente
Com o servidor ativo, abra um segundo terminal na mesma pasta e inicie o cliente:

```Bash
java Cliente
```

📸 Demonstração do Sistema
Autenticação e Menu Principal

<img width="403" height="63" alt="image" src="https://github.com/user-attachments/assets/902c70c7-cfaf-4ace-bf53-21320faded53" />


Execução de Consultas

<img width="778" height="301" alt="image" src="https://github.com/user-attachments/assets/26b89b59-e4cc-4fca-ae0a-6818a3f16ed2" />


