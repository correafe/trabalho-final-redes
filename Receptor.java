import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Random;
 
public class Receptor {
    private static final int PORTA = 5000;
    private static int expectedSeqNum = 0;
    private static double probPerda = 0.0;
 
    private static int pacotesAceitos = 0;
    private static int pacotesDescartados = 0;
    
    //Ponto de entrada principal do Receptor.
    //Mantém o socket aberto escutando requisições, aplica a lógica GBN de aceitação
    //de pacotes em ordem, simula a perda na rede e grava os dados no disco.
    public static void main(String[] args) throws Exception {
        DatagramSocket socket = new DatagramSocket(PORTA);
        System.out.println("Receptor aguardando na porta " + PORTA + "...");
 
        byte[] buffer = new byte[1035];
        FileOutputStream fos = null;
        Random random = new Random();
 
        InetAddress emissorAddress = null;
        int emissorPort = -1;
        String pathArquivo = "";
 
        while (true) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
 
            Segmento seg = Segmento.fromByteArray(packet.getData(), packet.getLength());
            emissorAddress = packet.getAddress();
            emissorPort = packet.getPort();
 
            if (seg.tipo == 2) {
                String payload = new String(seg.dados, StandardCharsets.UTF_8);
                String[] params = payload.split(";");
                probPerda = Double.parseDouble(params[0]);
                pathArquivo = params[1];
 
                fos = new FileOutputStream(pathArquivo);
                System.out.println("Handshake recebido. Arquivo: " + pathArquivo + " | Prob. Perda: " + probPerda);
 
                enviarAck(socket, emissorAddress, emissorPort, -1);
                continue;
            }
 
            if (seg.tipo == 0) {
                // Verifica se o pacote recebido é exatamente o próximo pacote esperado na sequência (garantia de entrega em ordem)
                if (seg.numSeq == expectedSeqNum) {
                    
                    // Sorteia um valor de 0.0 a 1.0; se for menor que a probabilidade, simula a perda descartando o pacote (sem enviar ACK)
                    if (random.nextDouble() < probPerda) {
                        pacotesDescartados++;
                        System.out.println("SIMULAÇÃO DE PERDA: Pacote seq=" + seg.numSeq + " descartado.");
                        continue;
                    }
 
                    pacotesAceitos++;
                    fos.write(seg.dados);
                    System.out.println("Recebido seq=" + seg.numSeq + ". Enviando ACK.");
                    enviarAck(socket, emissorAddress, emissorPort, expectedSeqNum);
                    expectedSeqNum++;
                } else {
                    // Pacote fora de ordem: descarta os dados e reenvia o ACK cumulativo do último pacote aceito em ordem (expectedSeqNum - 1)
                    System.out.println("Fora de ordem seq=" + seg.numSeq
                            + " (esperado: " + expectedSeqNum + "). Reenviando último ACK.");
                    enviarAck(socket, emissorAddress, emissorPort, expectedSeqNum - 1);
                }
            }
 
            if (seg.tipo == 3) {
                System.out.println("Pacote FIN recebido. Encerrando transferência.");
                enviarAck(socket, emissorAddress, emissorPort, seg.numSeq);
                if (fos != null) fos.close();
 
                try {
                    MessageDigest md = MessageDigest.getInstance("MD5");
                    byte[] hash = md.digest(Files.readAllBytes(Paths.get(pathArquivo)));
                    StringBuilder sb = new StringBuilder();
                    for (byte b : hash) sb.append(String.format("%02x", b));
                    System.out.println("\n[VALIDAÇÃO] Hash MD5 do ficheiro recebido: " + sb.toString());
                } catch (Exception e) {
                    System.out.println("Erro ao calcular MD5: " + e.getMessage());
                }
 
                break;
            }
        }
 
        socket.close();
 
        System.out.println("\n--- Estatísticas do Receptor ---");
        System.out.println("Total de pacotes de dados aceitos: " + pacotesAceitos);
        System.out.println("Total de pacotes descartados (simulação): " + pacotesDescartados);
        int totalTentativasEmOrdem = pacotesAceitos + pacotesDescartados;
        double taxaEfetiva = totalTentativasEmOrdem > 0
                ? ((double) pacotesDescartados / totalTentativasEmOrdem) * 100
                : 0;
        System.out.printf("Taxa de perda efetiva: %.2f%%\n", taxaEfetiva);
    }
 
    //Cria e dispara um pacote de reconhecimento (ACK) de volta ao emissor.
    //Confirma o recebimento cumulativo do pacote especificado por 'numAck'.
    private static void enviarAck(DatagramSocket socket, InetAddress address, int port, int numAck) throws Exception {
        Segmento ack = new Segmento((byte) 1, 0, numAck, null);
        byte[] ackData = ack.toByteArray();
        socket.send(new DatagramPacket(ackData, ackData.length, address, port));
    }
}