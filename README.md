
# Sistema Distribuído de Controle Colaborativo com Tolerância a Falhas

## 1\. Visão Geral

Este projeto, desenvolvido para a disciplina de Sistemas Distribuídos, é uma simulação em Java de um ambiente de edição colaborativa onde múltiplos nós concorrem para modificar um documento de texto compartilhado. A principal característica do sistema é a sua **alta disponibilidade e robustez**: ele é capaz de detetar a falha do nó coordenador (líder), eleger autonomamente um novo líder entre os processos sobreviventes e continuar a sua operação, restaurando o estado a partir do último ponto de verificação (checkpoint) conhecido.

O sistema demonstra a aplicação prática de múltiplos conceitos fundamentais de sistemas distribuídos, incluindo:

* Exclusão mútua centralizada para controle de acesso a recursos críticos.
* Ordenação causal de eventos com Relógios de Lamport.
* Replicação de dados para consistência eventual.
* Um mecanismo completo de *failover* com deteção de falhas e eleição de líder (Algoritmo Bully).

## 2\. Funcionalidades Principais

* **Arquitetura Híbrida e Dinâmica:** O sistema opera com 4 nós, onde um deles assume dinamicamente o papel de Coordenador. Se o coordenador atual falhar, o sistema não para; em vez disso, ele se reconfigura autonomamente.

* **Exclusão Mútua Centralizada:** O acesso ao documento compartilhado é estritamente controlado pelo Coordenador. Um nó deve solicitar permissão, aguardar na fila se o recurso estiver ocupado e só pode editar o documento após receber a concessão, garantindo que nunca ocorram edições simultâneas.

* **Controle de Concorrência com Relógios de Lamport:** Para garantir uma ordem justa и causal, todas as requisições de acesso são marcadas com um timestamp lógico de Lamport. O Coordenador utiliza uma fila de prioridade que ordena os pedidos por este timestamp (e pelo ID do nó como critério de desempate), assegurando que as requisições sejam processadas de forma ordenada.

* **Replicação Passiva e Consistência Eventual:** Cada nó mantém uma réplica local do documento. Após uma edição ser concluída e liberada, o Coordenador atualiza a sua versão "mestre" e propaga esta atualização de forma assíncrona para todos os outros nós, garantindo que, eventualmente, todo o sistema convirja para o mesmo estado consistente.

* **Tolerância a Falhas (Failover e Recuperação):**

   * **Deteção de Falha:** A falha do Coordenador é detetada de forma implícita e eficiente através de `IOException` nas conexões TCP, o que imediatamente aciona o processo de recuperação.
   * **Eleição de Líder (Algoritmo Bully):** Ao detetar a falha, os nós iniciam o Algoritmo Bully. Eles comunicam entre si para eleger o nó ativo com o maior ID como o novo Coordenador.
   * **Checkpoints e Recuperação de Estado:** O Coordenador ativo salva o estado do documento em um ficheiro (`checkpoint.dat`) a cada 30 segundos. O novo líder eleito restaura o documento a partir deste ficheiro, garantindo a durabilidade dos dados e a continuidade da operação.
   * **Rollback:** Se um nó falhar enquanto está a editar o documento, o Coordenador deteta a desconexão, descarta a alteração que nunca foi confirmada (rollback) e libera o recurso para o próximo da fila, evitando bloqueios no sistema.

## 3\. Tecnologias Utilizadas

* **Linguagem:** Java (versão 8 ou superior)
* **Build Tool:** Apache Maven (para gestão de dependências e empacotamento)
* **Comunicação:** Sockets TCP/IP para toda a comunicação entre os nós, garantindo fiabilidade tanto na troca de mensagens de controle como na deteção de falhas.

## 4\. Estrutura do Projeto

```
Atividade_7/
│
├── src/
│   └── main/
│       └── java/
│           └── controlecolaborativo/
│               ├── Simulador.java          # Classe principal que orquestra a simulação.
│               │
│               ├── comum/                  # Classes de modelo partilhadas.
│               │   ├── Documento.java
│               │   ├── Logger.java
│               │   ├── Mensagem.java
│               │   └── PedidoAcesso.java
│               │
│               ├── coordenador/            # Lógica do serviço do Coordenador.
│               │   ├── ServicoCoordenador.java
│               │   └── TratadorNo.java
│               │
│               └── no/                     # Lógica principal dos nós.
│                   └── No.java
│
├── pom.xml                                 # Ficheiro de configuração do Maven.
├── COMPILAR.bat                            # Script para compilar o projeto.
├── EXECUTAR_SISTEMA.bat                    # Script para iniciar a simulação.
├── EXECUTAR_TUDO.bat                       # Script para compilar e executar tudo.
└── README.md                               # Este ficheiro.
```

## 5\. Como Executar

Para executar a simulação, certifique-se de que tem o **JDK 8 (ou superior)** e o **Apache Maven** instalados e configurados nas variáveis de ambiente do seu sistema.

### Opção 1: Execução Simplificada (Recomendado)

O script `EXECUTAR_TUDO.bat` automatiza todo o processo. Ele irá compilar o projeto e, em seguida, abrir uma nova janela do terminal para executar a simulação.

```bash
# Na raiz do projeto, execute:
./EXECUTAR_TUDO.bat
```

### Opção 2: Execução Passo a Passo

#### Passo 1: Compilar o Projeto

Execute o script `COMPILAR.bat`. Este script irá invocar o Maven para limpar compilações antigas, compilar todo o código-fonte e empacotar a aplicação num único ficheiro `.jar` executável com todas as dependências incluídas. Este ficheiro será gerado em `target/`.

```bash
# Na raiz do projeto, execute:
./COMPILAR.bat
```

Se a compilação for bem-sucedida, pode avançar para o próximo passo.

#### Passo 2: Iniciar a Simulação

Execute o script `EXECUTAR_SISTEMA.bat`. Este script inicia a classe `Simulador`, que orquestra um ciclo de vida completo para demonstração:

1.  **Início:** Cria e inicia 4 processos (nós). O sistema elege P4 (o nó de maior ID) como o coordenador inicial.
2.  **Operação Normal (45 segundos):** O sistema opera normalmente por 45 segundos. Durante este tempo, poderá observar nos logs os nós a solicitarem acesso, a editarem o documento e o coordenador a gerir a fila.
3.  **Simulação de Falha:** Após 45 segundos, o simulador força a falha do coordenador P4.
4.  **Recuperação e Nova Liderança (60 segundos):** Os nós restantes detetarão a falha e iniciarão o processo de eleição. P3 (o nó de maior ID restante) será eleito, restaurará o estado a partir do último checkpoint e o sistema continuará a sua operação sob a nova liderança por mais 60 segundos.
5.  **Encerramento Automático:** Após o período de operação com o novo líder, a simulação será **finalizada automaticamente**, e a janela do terminal fechará.

<!-- end list -->

```bash
# Na raiz do projeto, execute:
./EXECUTAR_SISTEMA.bat
```