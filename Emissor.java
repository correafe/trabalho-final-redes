import java.io.File;
import java.io.FileInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
 
public class Emissor {
    private static int base = 0;
    private static int nextSeqNum = 0;
    private static int N;
    private static Timer timer;
    private static final int TIMEOUT_MS = 1000;
 
    private static ArrayList<Segmento> bufferPacotes = new ArrayList<>();
 
    private static int pacotesEnviados = 0;
    private static int pacotesRetransmitidos = 0;
    private static int acksRecebidos = 0;
 
    
    //Ponto de entrada principal do Emissor.
    //Lê o arquivo para a memória, negocia o handshake inicial, gerencia a janela 
    //deslizante de transmissão e coordena as threads de envio e recebimento de ACKs.
    
    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.out.println("Uso: java Emissor <arquivo_origem> <IP_destino>:<path_destino> <tamanho_janela> <prob_perda>");
            return;
        }
 
        String arquivoOrigem = args[0];
        String[] destino = args[1].split(":");
        InetAddress ipDestino = InetAddress.getByName(destino[0]);
        String pathDestino = destino[1];
        N = Integer.parseInt(args[2]);
        String probPerda = args[3];
 
        int portaDestino = 5000;
        DatagramSocket socket = new DatagramSocket();
 
        File file = new File(arquivoOrigem);
        FileInputStream fis = new FileInputStream(file);
 
        String hsPayload = probPerda + ";" + pathDestino + ";" + file.length();
        Segmento handshake = new Segmento((byte) 2, 0, 0, hsPayload.getBytes(StandardCharsets.UTF_8));
        enviarSegmento(socket, handshake, ipDestino, portaDestino);
        System.out.println("Handshake enviado. Aguardando ACK do handshake...");
 
        byte[] hsAckBuffer = new byte[1035];
        DatagramPacket ackPacket = new DatagramPacket(hsAckBuffer, hsAckBuffer.length);
        socket.receive(ackPacket);
        System.out.println("ACK do Handshake recebido. Iniciando transferência de dados...");
 
        byte[] dadosPayload = new byte[1024];
        int bytesRead;
        int seqCounter = 0;
        while ((bytesRead = fis.read(dadosPayload)) != -1) {
            byte[] actualData = new byte[bytesRead];
            System.arraycopy(dadosPayload, 0, actualData, 0, bytesRead);
            bufferPacotes.add(new Segmento((byte) 0, seqCounter++, 0, actualData));
        }
        fis.close();
        int totalPacotes = bufferPacotes.size();
 
        Thread ackReceiver = new Thread(() -> {
            byte[] threadAckBuffer = new byte[1035];
            try {
                while (base < totalPacotes) {
                    DatagramPacket rcvPacket = new DatagramPacket(threadAckBuffer, threadAckBuffer.length);
                    socket.receive(rcvPacket);
                    Segmento ackSeg = Segmento.fromByteArray(rcvPacket.getData(), rcvPacket.getLength());
 
                    if (ackSeg.tipo == 1) {
                        synchronized (Emissor.class) {
                            acksRecebidos++;
                            if (ackSeg.numAck >= base) {
                                base = ackSeg.numAck + 1;
                                System.out.println("ACK cumulativo recebido: " + ackSeg.numAck
                                        + " | Janela avançou, base agora é: " + base);
                                pararTimer();
                                if (base < nextSeqNum) {
                                    iniciarTimer(socket, ipDestino, portaDestino);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                if (!socket.isClosed()) e.printStackTrace();
            }
        });
        ackReceiver.start();
 
        long startTime = System.currentTimeMillis();
 
        while (base < totalPacotes) {
            synchronized (Emissor.class) {
                if (nextSeqNum < base + N && nextSeqNum < totalPacotes) {
                    Segmento seg = bufferPacotes.get(nextSeqNum);
                    enviarSegmento(socket, seg, ipDestino, portaDestino);
                    System.out.println("Enviado pacote seq=" + nextSeqNum);
 
                    if (base == nextSeqNum) {
                        iniciarTimer(socket, ipDestino, portaDestino);
                    }
                    nextSeqNum++;
                }
            }
            Thread.sleep(5);
        }
 
        ackReceiver.join();
 
        Segmento fin = new Segmento((byte) 3, nextSeqNum, 0, null);
        enviarSegmento(socket, fin, ipDestino, portaDestino);
        System.out.println("Arquivo transmitido. FIN enviado.");
 
        long endTime = System.currentTimeMillis();
        double tempoSegundos = (endTime - startTime) / 1000.0;
        double throughput = (file.length() / 1024.0) / tempoSegundos;
 
        socket.close();
 
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(Files.readAllBytes(Paths.get(arquivoOrigem)));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            System.out.println("\n[VALIDAÇÃO] Hash MD5 do ficheiro original: " + sb.toString());
        } catch (Exception e) {
            System.out.println("Erro ao calcular MD5: " + e.getMessage());
        }
 
        System.out.println("\n--- Estatísticas do Emissor ---");
        System.out.println("Total de pacotes enviados (sem retransmissões): " + pacotesEnviados);
        System.out.println("Total de pacotes retransmitidos: " + pacotesRetransmitidos);
        System.out.println("Total de ACKs recebidos: " + acksRecebidos);
        System.out.printf("Throughput estimado: %.2f KB/s\n", throughput);
    }
 
    
    //Encapsula o segmento em um pacote UDP e o despacha pela rede.
    //Também contabiliza as estatísticas de envio caso seja um pacote de dados (Tipo 0).
    
    private static void enviarSegmento(DatagramSocket socket, Segmento seg, InetAddress ip, int porta) throws Exception {
        byte[] data = seg.toByteArray();
        socket.send(new DatagramPacket(data, data.length, ip, porta));
        if (seg.tipo == 0) pacotesEnviados++;
    }
 
    
    //Inicia ou reinicia de forma sincronizada o temporizador de segurança.
    //Caso o tempo limite estoure (timeout), aciona a FSM do GBN para retransmitir
    //imediatamente toda a janela pendente (do pacote 'base' até 'nextSeqNum' - 1).
    
    private static synchronized void iniciarTimer(DatagramSocket socket, InetAddress ipDestino, int portaDestino) {
        pararTimer();
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                synchronized (Emissor.class) {
                    System.out.println("!!! TIMEOUT !!! Retransmitindo janela a partir da base: " + base);
                    try {
                        for (int i = base; i < nextSeqNum; i++) {
                            byte[] data = bufferPacotes.get(i).toByteArray();
                            socket.send(new DatagramPacket(data, data.length, ipDestino, portaDestino));
                            pacotesRetransmitidos++;
                            System.out.println("Re-enviado pacote seq=" + i);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    iniciarTimer(socket, ipDestino, portaDestino);
                }
            }
        }, TIMEOUT_MS);
    }
 
    
    //Cancela o temporizador atual de forma segura contra concorrência.
    //Chamado sempre que um ACK cumulativo válido avança a base da janela.
    
    private static synchronized void pararTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }
}