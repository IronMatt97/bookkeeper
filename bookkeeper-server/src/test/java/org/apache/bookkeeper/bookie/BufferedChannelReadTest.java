package org.apache.bookkeeper.bookie;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.internal.matchers.Null;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collection;

@RunWith(Parameterized.class)
public class BufferedChannelReadTest {
    FileChannel fileChannel;
    BufferedChannel bufferedChannel;
    ByteBuf byteBuf;
    Object expectedParam;
    int position;
    int length;

    public BufferedChannelReadTest(TestInput testInput) {
        this.fileChannel = testInput.fileChannel;
        this.bufferedChannel = testInput.bufferedChannel;
        this.expectedParam = testInput.expectedParam;
        this.byteBuf = testInput.byteBuf;
        this.position = testInput.position;
        this.length = testInput.length;
    }
    public static Collection<TestInput[]> configure() throws IOException {

        //Parametri di configurazione
        Collection<TestInput> inputs = new ArrayList<>();
        Collection<TestInput[]> result = new ArrayList<>();
        File file;  //Il file su cui scrivere
        FileChannel fileChannel;    //Canale di scrittura sul file
        ByteBufAllocator allocator = UnpooledByteBufAllocator.DEFAULT; //Allocatore di memoria per il buffer di bytes
        BufferedChannel bufferedChannel=null;    //Canale di scrittura sul buffer (classe su cui testare il metodo write)
        ByteBuf byteBuf = Unpooled.buffer(1);   //buffer di bytes di lunghezza 1 (1 array)
        byte[] b = {1,2,3};
        byteBuf.writeBytes(b);
        int capacity = 5;
        int position;   //Da dove leggere
        int length;     //Quanti byte leggere

        /*
        Test 1 -> (branch 265-272)
        Usando questo costruttore diverso, il WriterBufferStartPosition risulta spostato (3) dopo la pos richiesta (0),
        così da saltare i primi branch; la capacità di scrittura è 1, quindi vengono finalizzate più scritture spostando
        il primo valore. La prima volta entra nel branch 272, il che modifica l'indice di lettura; le successive volte
        quindi entrerà nel branch 265 rendendo vero il confronto. C'è una "seconda volta" in quanto la volta che entra
        nell'else non diminuisce il length, così da poter effettuare una seconda iterazione.
        */
        position = 0; length = 1;
        file = File.createTempFile("test", "log");
        file.deleteOnExit();
        fileChannel = new RandomAccessFile(file, "rw").getChannel();
        bufferedChannel = new BufferedChannel(allocator, fileChannel, 1,1,0);
        bufferedChannel.write(byteBuf);
        inputs.add(new TestInput(fileChannel,bufferedChannel,byteBuf,position,length,1));

        /*
        Test 2 -> Caso di lettura dal mezzo del WriteBuffer senza arrivare ad EOF (branch 250).
        In questo caso utilizzo il costruttore base per dare una capacità maggiore della scrittura effettuata; in questo
        caso non viene spostato il WriterBufferStartPosition, ed entra nei primi brach
        */
        position = 2; length = 1;
        file = File.createTempFile("test", "log");
        file.deleteOnExit();
        fileChannel = new RandomAccessFile(file, "rw").getChannel();
        bufferedChannel = new BufferedChannel(allocator, fileChannel, capacity);
        bufferedChannel.write(byteBuf);
        inputs.add(new TestInput(fileChannel,bufferedChannel,byteBuf,position,length,1));

        /*
        Test 3 -> Caso di raggiungimento di EOF (branch 254) -> i primi riesce a leggerli comunque.
        In questo caso succede la stessa cosa del test di prima, ma si va anche oltre l'EOF riuscendo a prendere
        il branch di eccezione
        */
        position = 2; length = 5;
        file = File.createTempFile("test", "log");
        file.deleteOnExit();
        fileChannel = new RandomAccessFile(file, "rw").getChannel();
        bufferedChannel = new BufferedChannel(allocator, fileChannel, capacity);
        bufferedChannel.write(byteBuf);
        inputs.add(new TestInput(fileChannel,bufferedChannel,byteBuf,position,length,1));

        /*
        Test 4 -> Caso di writeBuffer nullo (branch 261) NON POSSIBILE CON I COSTRUTTORI A DISPOSIZIONE

        position = 0; length = 1;
        file = File.createTempFile("test", "log");
        file.deleteOnExit();
        fileChannel = new RandomAccessFile(file, "rw").getChannel();
        bufferedChannel = new BufferedChannel(allocator, fileChannel, -1,capacity,0);
        inputs.add(new TestInput(fileChannel,bufferedChannel,byteBuf,position,length,0));
        */

        /*
        Test 5 -> Caso di ByteBuf nullo
        */
        position = 0; length = 5;
        file = File.createTempFile("test", "log");
        file.deleteOnExit();
        fileChannel = new RandomAccessFile(file, "rw").getChannel();
        bufferedChannel = new BufferedChannel(allocator, fileChannel, capacity);
        bufferedChannel.write(byteBuf);
        inputs.add(new TestInput(fileChannel,bufferedChannel,null,position,length,1));

        /*
        Test 6 -> caso di position negativa
        */
        position = -1; length = 1;
        file = File.createTempFile("test", "log");
        file.deleteOnExit();
        fileChannel = new RandomAccessFile(file, "rw").getChannel();
        bufferedChannel = new BufferedChannel(allocator, fileChannel, 1,1,0);
        bufferedChannel.write(byteBuf);
        inputs.add(new TestInput(fileChannel,bufferedChannel,byteBuf,position,length,0));

        /*
        Test 7 e 8 -> caso di length e nulla
        */
        position = 0;
        file = File.createTempFile("test", "log");
        file.deleteOnExit();
        fileChannel = new RandomAccessFile(file, "rw").getChannel();
        bufferedChannel = new BufferedChannel(allocator, fileChannel, 1,1,0);
        bufferedChannel.write(byteBuf);
        inputs.add(new TestInput(fileChannel,bufferedChannel,byteBuf,position,0,0));
        inputs.add(new TestInput(fileChannel,bufferedChannel,byteBuf,position,-1,0));

        for (TestInput e : inputs) {
            result.add(new TestInput[] { e });
        }
        return result;

    }
    @Parameters
    public static Collection<TestInput[]> getTestParameters() throws IOException {
        return configure();
    }
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void read_test() throws IOException {

        //Mi aspetto IOException quando avviene una lettura oltre l'EOF
        if(this.bufferedChannel.readCapacity - this.position<this.length)
            thrown.expect(IOException.class);
        if (this.byteBuf == null)
            thrown.expect(NullPointerException.class);
        if (this.position < 0)
            thrown.expect(IllegalArgumentException.class);

        int read = bufferedChannel.read(this.byteBuf,this.position,this.length);
        Assert.assertEquals(expectedParam,read);

        fileChannel.close();
        bufferedChannel.close();
    }
    private static class TestInput {
        FileChannel fileChannel;
        BufferedChannel bufferedChannel;
        ByteBuf byteBuf;
        Object expectedParam;
        int position;
        int length;
        public TestInput(FileChannel fileChannel, BufferedChannel bufferedChannel, ByteBuf byteBuf, int position, int length, Object expectedParam) {
            this.fileChannel = fileChannel;
            this.bufferedChannel = bufferedChannel;
            this.expectedParam = expectedParam;
            this.position = position;
            this.length = length;
            this.byteBuf = byteBuf;
        }
    }
}
