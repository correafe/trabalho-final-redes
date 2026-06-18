# Implementação do Protocolo Go-Back-N em Java via UDP

Este projeto implementa a transferência confiável de dados utilizando o protocolo Go-Back-N (GBN) sobre sockets UDP, desenvolvido em Java padrão sem frameworks externos.

## Pré-requisitos
- Java Development Kit (JDK) 8 ou superior instalado.

## Como Compilar
Abra o terminal na pasta raiz do projeto (onde se encontram os ficheiros `.java`) e execute:
\`\`\`bash
javac *.java
\`\`\`

## Como Executar

O sistema é composto por dois módulos independentes: o **Receptor** e o **Emissor**. O Receptor deve ser iniciado primeiro.

### 1. Iniciar o Receptor
Num terminal, inicie o servidor que ficará a escuta na porta 5000:
\`\`\`bash
java Receptor
\`\`\`

### 2. Iniciar o Emissor
Noutro terminal, execute o emissor utilizando a seguinte sintaxe obrigatória:
\`\`\`bash
java Emissor <arquivo_origem> <IP_destino>:<path_destino> <tamanho_janela> <prob_perda>
\`\`\`

**Parâmetros:**
- `<arquivo_origem>`: Caminho do ficheiro local a ser enviado.
- `<IP_destino>`: Endereço IP da máquina onde o Receptor está a correr (use `127.0.0.1` para testes locais).
- `<path_destino>`: Nome ou caminho onde o ficheiro será guardado no destino.
- `<tamanho_janela>`: Tamanho máximo da janela deslizante do Go-Back-N (ex: 8).
- `<prob_perda>`: Valor decimal entre 0.0 e 1.0 para simular a perda de pacotes na rede (ex: 0.10 para 10% de probabilidade de perda).

### Exemplo Prático de Teste (Localhost)
Para testar o envio do ficheiro `teste.txt` com uma janela de 8 pacotes e 10% de probabilidade de erro simulada:
\`\`\`bash
java Emissor teste.txt 127.0.0.1:recebido.txt 8 0.10
\`\`\`

## Funcionalidades Implementadas
- **Go-Back-N FSM:** Máquina de estados completa para emissor e recetor.
- **Janela Deslizante:** Suporte a concorrência com threads para envio e receção simultânea de ACKs.
- **Simulação de Perdas:** Descarte aleatório de pacotes no recetor.
- **Checksum MD5:** Validação de integridade automática no final de cada transferência.