import java.nio.ByteBuffer;
 
public class Segmento {
    public byte tipo;
    public int numSeq;
    public int numAck;
    public short tamanhoDados;
    public byte[] dados;
    
    //Inicializa um novo segmento com os cabeçalhos do protocolo.
    //O tamanho do payload (dados) é calculado automaticamente.
    public Segmento(byte tipo, int numSeq, int numAck, byte[] dados) {
        this.tipo = tipo;
        this.numSeq = numSeq;
        this.numAck = numAck;
        this.dados = dados;
        this.tamanhoDados = (dados != null) ? (short) dados.length : 0;
    }
    
    //Serializa o objeto Segmento em um array de bytes.
    //Prepara os dados de forma estruturada para transmissão via datagrama UDP.
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
    
    //Desserializa um array de bytes recebido da rede via UDP.
    //Converte os bytes puros de volta para um objeto estruturado da classe Segmento.
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