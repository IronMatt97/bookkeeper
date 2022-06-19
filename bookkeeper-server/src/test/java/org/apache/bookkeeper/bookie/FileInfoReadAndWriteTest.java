
package org.apache.bookkeeper.bookie;

import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Stream;

@RunWith(Parameterized.class)
public class FileInfoReadAndWriteTest {
    FileInfo fileInfo;
    long write_position;
    long read_position;
    ByteBuffer[] write_buffer;
    ByteBuffer read_buffer;
    boolean bestEffort;
    Object expectedParamR;
    Object expectedParamW;

    public FileInfoReadAndWriteTest(TestInput t) {
        this.write_position = t.write_position;
        this.read_position = t.read_position;
        this.write_buffer = t.write_buffer;
        this.read_buffer = t.read_buffer;
        this.fileInfo = t.fileInfo;
        this.bestEffort = t.bestEffort;
        this.expectedParamR = t.expectedParamR;
        this.expectedParamW = t.expectedParamW;
    }

    public static Collection<TestInput[]> configure() throws IOException {

        //Parametri di configurazione
        Collection<TestInput> inputs = new ArrayList<>();
        Collection<TestInput[]> result = new ArrayList<>();

        FileInfo fi;
        File file;
        long write_position;
        long read_position;
        boolean bestEffort;
        ByteBuffer[] write_buffer;
        ByteBuffer read_buffer;
        Object expectedParamR;
        Object expectedParamW;

        /*
        Test 1 -> Test basico
        */
        file = new File("tmp","file.log");
        file.deleteOnExit();
        fi = new FileInfo(file,"MasterKey".getBytes(StandardCharsets.UTF_8),FileInfo.V1);
        write_buffer = new ByteBuffer[2];
        read_buffer = ByteBuffer.allocate(100);
        write_buffer[0] = ByteBuffer.wrap("Test0".getBytes(StandardCharsets.UTF_8));
        write_buffer[1] = ByteBuffer.wrap("Test1".getBytes(StandardCharsets.UTF_8));
        write_position = 0;
        read_position = 0;
        expectedParamW = 10L;
        expectedParamR = 10L;
        bestEffort = true;
        inputs.add(new TestInput(fi, write_position, read_position, write_buffer,
                read_buffer,bestEffort, expectedParamW, expectedParamR));

        /*
        Test 2 -> Short read (branch 432)
        */
        file = new File("tmp","file.log");
        file.deleteOnExit();
        fi = new FileInfo(file,"MasterKey".getBytes(StandardCharsets.UTF_8),FileInfo.V1);
        write_buffer = new ByteBuffer[2];
        read_buffer = ByteBuffer.allocate(100);
        write_buffer[0] = ByteBuffer.wrap("T1".getBytes(StandardCharsets.UTF_8));
        write_buffer[1] = ByteBuffer.wrap("T2".getBytes(StandardCharsets.UTF_8));
        write_position = 0;
        read_position = 0;
        expectedParamW = 4L;
        expectedParamR = 4L;
        bestEffort = false;
        inputs.add(new TestInput(fi, write_position, read_position, write_buffer,
                read_buffer,bestEffort, expectedParamW, expectedParamR));

        /*
        Test 3 -> Lettura non effettuata per mancanza di spazio nel read buffer(return 439)
        */
        file = new File("tmp","file.log");
        file.deleteOnExit();
        fi = new FileInfo(file,"MasterKey".getBytes(StandardCharsets.UTF_8),FileInfo.V1);
        write_buffer = new ByteBuffer[2];
        read_buffer = ByteBuffer.allocate(0);
        write_buffer[0] = ByteBuffer.wrap("T1".getBytes(StandardCharsets.UTF_8));
        write_buffer[1] = ByteBuffer.wrap("T2".getBytes(StandardCharsets.UTF_8));
        write_position = 0;
        read_position = 0;
        expectedParamW = 4L;
        expectedParamR = 0L;
        bestEffort = true;
        inputs.add(new TestInput(fi, write_position, read_position, write_buffer,
                read_buffer,bestEffort, expectedParamW, expectedParamR));

        /*
        Test 4 -> null test
        */

        inputs.add(new TestInput(null, 0, 0, null,
                null,true, 0, 0));

        /*
        Test 5 -> Indici negativi
        */
        file = new File("tmp","file.log");
        file.deleteOnExit();
        fi = new FileInfo(file,"MasterKey".getBytes(StandardCharsets.UTF_8),FileInfo.V1);
        write_buffer = new ByteBuffer[2];
        read_buffer = ByteBuffer.allocate(0);
        write_buffer[0] = ByteBuffer.wrap("T1".getBytes(StandardCharsets.UTF_8));
        write_buffer[1] = ByteBuffer.wrap("T2".getBytes(StandardCharsets.UTF_8));
        write_position = -1;
        read_position = -1;
        expectedParamW = 4L;
        expectedParamR = 0L;
        bestEffort = true;
        inputs.add(new TestInput(fi, write_position, read_position, write_buffer,
                read_buffer,bestEffort, expectedParamW, expectedParamR));

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

    @BeforeClass
    public static void setUpEnvironment() {
        // Create the directories if do not exist
        if (!Files.exists(Paths.get("tmp"))) {
            File tmpDir = new File("tmp");
            tmpDir.mkdir();
        }

        // Delete test file if exists
        if (Files.exists(Paths.get("tmp", "file.log"))) {
            File testFile = new File("tmp", "file.log");
            testFile.delete();
        }
    }

    @Test
    public void writeAndRead_test() throws IOException {

        if (bestEffort == false)
            thrown.expect(ShortReadException.class);
        if (fileInfo == null)
            thrown.expect(NullPointerException.class);

        long numBytesWritten = fileInfo.write(write_buffer,write_position);
        Assert.assertEquals(expectedParamW,numBytesWritten);
        long numBytesRead = fileInfo.read(read_buffer,read_position,bestEffort);
        Assert.assertEquals(expectedParamR,numBytesRead);

    }

    private static class TestInput {
        FileInfo fileInfo;
        long write_position;
        long read_position;
        boolean bestEffort;
        ByteBuffer[] write_buffer;
        ByteBuffer read_buffer;
        Object expectedParamR;
        Object expectedParamW;
        public TestInput(FileInfo fileInfo, long write_position, long read_position, ByteBuffer[] write_buffer,
                         ByteBuffer read_buffer, boolean bestEffort, Object expectedParamW, Object expectedParamR) {
            this.write_position = write_position;
            this.read_position = read_position;
            this.write_buffer = write_buffer;
            this.read_buffer = read_buffer;
            this.fileInfo = fileInfo;
            this.bestEffort = bestEffort;
            this.expectedParamR = expectedParamR;
            this.expectedParamW = expectedParamW;
        }
    }
}
