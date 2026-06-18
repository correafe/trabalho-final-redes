
import java.nio.ByteBuffer;
 
public class Segmento {
    public byte tipo; // 0=DATA, 1=ACK, 2=HANDSHAKE, 3=FIN
    public int numSeq;
    public int numAck;
    public short tamanhoDados;
    public byte[] dados;
 
    public Segmento(byte tipo, int numSeq, int numAck, byte[] dados) {
        this.tipo = tipo;
        this.numSeq = numSeq;
        this.numAck = numAck;
        this.dados = dados;
        this.tamanhoDados = (dados != null) ? (short) dados.length : 0;
    }
 
    // Transforma o objeto em um array de bytes para envio via UDP
    public byte[] toByteArray() {
        int tamanhoTotal = 11 + (dados != null ? dados.length : 0);
        ByteBuffer buffer = ByteBuffer.allocate(tamanhoTotal);
        buffer.put(tipo);
        buffer.putInt(numSeq);
        buffer.putInt(numAck);
        buffer.putShort(tamanhoDados);
        if (dados != null && dados.length > 0) {
            buffer.put(dados);
        }
        return buffer.array();
    }
 
    // Reconstrói o objeto a partir de um array de bytes recebido via UDP
    public static Segmento fromByteArray(byte[] array, int length) {
        ByteBuffer buffer = ByteBuffer.wrap(array, 0, length);
        byte tipo = buffer.get();
        int numSeq = buffer.getInt();
        int numAck = buffer.getInt();
        short tamanhoDados = buffer.getShort();
 
        byte[] dados = null;
        if (tamanhoDados > 0) {
            dados = new byte[tamanhoDados];
            buffer.get(dados);
        }
        return new Segmento(tipo, numSeq, numAck, dados);
    }
}