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
    
    // Estatísticas
    private static int pacotesRecebidos = 0;
    private static int pacotesDescartados = 0;

    public static void main(String[] args) throws Exception {
        DatagramSocket socket = new DatagramSocket(PORTA);
        System.out.println("Receptor aguardando na porta " + PORTA + "...");

        byte[] buffer = new byte[1035]; // 11 bytes cabecalho + 1024 dados
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

            // HANDSHAKE (Tipo 2)
            if (seg.tipo == 2) {
                String payload = new String(seg.dados, StandardCharsets.UTF_8);
                String[] params = payload.split(";");
                probPerda = Double.parseDouble(params[0]);
                pathArquivo = params[1];
                
                fos = new FileOutputStream(pathArquivo);
                System.out.println("Handshake recebido. Arquivo: " + pathArquivo + " | Prob. Perda: " + probPerda);
                
                // Responde o Handshake (ACK)
                enviarAck(socket, emissorAddress, emissorPort, -1);
                continue;
            }

            // Simulação de Perda para pacotes de DATA (Tipo 0) e FIN (Tipo 3)
            if (seg.tipo == 0 || seg.tipo == 3) {
                if (random.nextDouble() < probPerda) {
                    pacotesDescartados++;
                    System.out.println("SIMULAÇÃO DE PERDA: Pacote seq=" + seg.numSeq + " descartado.");
                    continue; 
                }
            }

            // DADOS (Tipo 0)
            if (seg.tipo == 0) {
                pacotesRecebidos++;
                if (seg.numSeq == expectedSeqNum) {
                    // Pacote em ordem
                    fos.write(seg.dados);
                    System.out.println("Recebido seq=" + seg.numSeq + ". Enviando ACK.");
                    enviarAck(socket, emissorAddress, emissorPort, expectedSeqNum);
                    expectedSeqNum++;
                } else {
                    // Fora de ordem: reenvia ACK do último recebido corretamente
                    System.out.println("Fora de ordem seq=" + seg.numSeq + " (esperado: " + expectedSeqNum + "). Reenviando último ACK.");
                    enviarAck(socket, emissorAddress, emissorPort, expectedSeqNum - 1);
                }
            }

            // FIN (Tipo 3)
            if (seg.tipo == 3) {
                System.out.println("Pacote FIN recebido. Encerrando transferência.");
                enviarAck(socket, emissorAddress, emissorPort, seg.numSeq); // Confirma o FIN
                if (fos != null) fos.close();
                
                // === VERIFICAÇÃO DE INTEGRIDADE MD5 ===
                try {
                    MessageDigest md = MessageDigest.getInstance("MD5");
                    byte[] hash = md.digest(Files.readAllBytes(Paths.get(pathArquivo)));
                    StringBuilder sb = new StringBuilder();
                    for (byte b : hash) {
                        sb.append(String.format("%02x", b));
                    }
                    System.out.println("\n[VALIDAÇÃO] Hash MD5 do ficheiro recebido: " + sb.toString());
                } catch (Exception e) {
                    System.out.println("Erro ao calcular MD5: " + e.getMessage());
                }
                
                break;
            }
        }

        socket.close();
        
        // Exibe estatísticas finais
        System.out.println("\n--- Estatísticas do Receptor ---");
        System.out.println("Total de pacotes de dados recebidos: " + pacotesRecebidos);
        System.out.println("Total de pacotes descartados: " + pacotesDescartados);
        int totalTentativas = pacotesRecebidos + pacotesDescartados;
        double taxaEfetiva = totalTentativas > 0 ? ((double) pacotesDescartados / totalTentativas) * 100 : 0;
        System.out.printf("Taxa de perda efetiva: %.2f%%\n", taxaEfetiva);
    }

    private static void enviarAck(DatagramSocket socket, InetAddress address, int port, int numAck) throws Exception {
        Segmento ack = new Segmento((byte) 1, 0, numAck, null);
        byte[] ackData = ack.toByteArray();
        socket.send(new DatagramPacket(ackData, ackData.length, address, port));
    }
}