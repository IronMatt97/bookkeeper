package org.apache.bookkeeper.bookie;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collection;

@RunWith(Parameterized.class)
public class BufferedChannelWriteTest {
    FileChannel fileChannel;
    BufferedChannel bufferedChannel;
    ByteBuf byteBuf;
    Object expectedParam;

    public BufferedChannelWriteTest(TestInput testInput) {
        this.fileChannel = testInput.fileChannel;
        this.bufferedChannel = testInput.bufferedChannel;
        this.expectedParam = testInput.expectedParam;
        this.byteBuf = testInput.byteBuf;
    }
    public static Collection<TestInput[]> configure() throws IOException {

        //Parametri di configurazione
        Collection<TestInput> inputs = new ArrayList<>();
        Collection<TestInput[]> result = new ArrayList<>();
        File file;  //Il file su cui scrivere
        FileChannel fileChannel;    //Canale di scrittura sul file
        ByteBufAllocator allocator = UnpooledByteBufAllocator.DEFAULT; //Allocatore di memoria per il buffer di bytes
        BufferedChannel bufferedChannel;    //Canale di scrittura sul buffer (classe su cui testare il metodo write)
        ByteBuf byteBuf = Unpooled.buffer(1);   //buffer di bytes di lunghezza 1 (1 array)
        byte[] b = {1,2,3};
        byteBuf.writeBytes(b);
        int writeCapacity = 2;  //Lunghezza di scrittura massima permessa
        int readCapacity = 0;   //Lunghezza di lettura massima permessa
        int unpersistedBytesBound = 1;  //Quanti per volta ne puoi scrivere


        /*
        Test 1 -> Caso di saturazione del buffer (branch 130TF/135T/137T)
        Si vogliono scrivere 3 bytes anche se la capacità è 2; Tuttavia non verrà scritto nulla perchè
        il limite concesso è 1.
         */
        file = File.createTempFile("test", "log");
        file.deleteOnExit();
        fileChannel = new RandomAccessFile(file, "rw").getChannel();
        bufferedChannel = new BufferedChannel(allocator, fileChannel, writeCapacity,readCapacity,unpersistedBytesBound);
        inputs.add(new TestInput(fileChannel,bufferedChannel,byteBuf,0));

        /*
        Test 2 -> Caso di non saturazione del buffer(branch 137F)
        Si vogliono scrivere 3 bytes ma la capacità è 2, quindi alla fine solo 1 ne rimane (per via del flush).
        Questa volta il limite concesso è 10, quindi la scrittura avviene
         */
        unpersistedBytesBound=10;
        file = File.createTempFile("test", "log");
        file.deleteOnExit();
        fileChannel = new RandomAccessFile(file, "rw").getChannel();
        bufferedChannel = new BufferedChannel(allocator, fileChannel, writeCapacity,readCapacity,unpersistedBytesBound);
        inputs.add(new TestInput(fileChannel,bufferedChannel,byteBuf,1));

        /*
        Test 3 -> Caso in cui non si ha un limite di scrittura (branch 135F)
        Si vogliono scrivere 3 bytes su una capacità di 4, il limite non è proprio definito stavolta.
         */
        writeCapacity=4;
        file = File.createTempFile("test", "log");
        file.deleteOnExit();
        fileChannel = new RandomAccessFile(file, "rw").getChannel();
        bufferedChannel = new BufferedChannel(allocator, fileChannel, writeCapacity);
        inputs.add(new TestInput(fileChannel,bufferedChannel,byteBuf,3));

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
    public void write_test() throws IOException {

        bufferedChannel.write(this.byteBuf);
        Assert.assertEquals(expectedParam,bufferedChannel.getNumOfBytesInWriteBuffer());

        fileChannel.close();
        bufferedChannel.close();
    }
    private static class TestInput {
        FileChannel fileChannel;
        BufferedChannel bufferedChannel;
        ByteBuf byteBuf;
        Object expectedParam;
        public TestInput(FileChannel fileChannel, BufferedChannel bufferedChannel, ByteBuf byteBuf, Object expectedParam) {
            this.fileChannel = fileChannel;
            this.bufferedChannel = bufferedChannel;
            this.expectedParam = expectedParam;
            this.byteBuf = byteBuf;
        }
    }
}
