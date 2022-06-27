
package org.apache.bookkeeper.bookie;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.util.Source;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;

@RunWith(Parameterized.class)
public class FileInfoMoveToNewLocationTest {
    FileInfo fileInfo;
    File file;
    long sizeToMove;
    long position;
    ByteBuffer[] bytesToWrite;
    Object expectedParam;

    public FileInfoMoveToNewLocationTest(TestInput t) {
        this.position = t.position;
        this.bytesToWrite = t.bytesToWrite;
        this.fileInfo = t.fileInfo;
        this.file = t.file;
        this.sizeToMove = t.size;
        this.expectedParam = t.expectedParam;
    }

    public static Collection<TestInput[]> configure() throws IOException {

        //Parametri di configurazione
        Collection<TestInput> inputs = new ArrayList<>();
        Collection<TestInput[]> result = new ArrayList<>();


        int sizeToMove;
        long expectedParam;
        FileInfo fi;
        ByteBuffer[] byteBuffer;
        File file0;
        File file1;

        /*
        Test 1 -> Test basico per trasferimento a buon fine
        */
        file0 = new File("tmp","file0.log");
        file1 = new File("tmp","file1.log");
        file0.deleteOnExit();
        file1.deleteOnExit();
        fi = new FileInfo(file0,"MasterKey".getBytes(StandardCharsets.UTF_8),FileInfo.V0);
        byteBuffer = new ByteBuffer[2];
        byteBuffer[0] = ByteBuffer.wrap("Test0".getBytes(StandardCharsets.UTF_8));
        byteBuffer[1] = ByteBuffer.wrap("Test1".getBytes(StandardCharsets.UTF_8));
        sizeToMove= 4;
        expectedParam = 10L;
        inputs.add(new TestInput(fi,file1,sizeToMove,0L,byteBuffer,expectedParam));

        /*
        Test 2 -> Test per stesso riferimento da file0 a file1 (branch 507)
        */
        file0 = new File("tmp","file0.log");
        file1 = file0;
        file0.deleteOnExit();
        fi = new FileInfo(file0,"MasterKey".getBytes(StandardCharsets.UTF_8),FileInfo.V0);
        byteBuffer = new ByteBuffer[2];
        byteBuffer[0] = ByteBuffer.wrap("Test0".getBytes(StandardCharsets.UTF_8));
        byteBuffer[1] = ByteBuffer.wrap("Test1".getBytes(StandardCharsets.UTF_8));
        sizeToMove= 1034;
        expectedParam = 10L;
        inputs.add(new TestInput(fi,file1,sizeToMove,0L,byteBuffer,expectedParam));

        /*
        Test 3 -> Test per size da spostare maggiore della massima dimensione del filechannel (1034) (branch 510)
         */
        file0 = new File("tmp","file0.log");
        file1 = new File("tmp","file1.log");
        file0.deleteOnExit();
        file1.deleteOnExit();
        fi = new FileInfo(file0,"MasterKey".getBytes(StandardCharsets.UTF_8),FileInfo.V0);
        byteBuffer = new ByteBuffer[2];
        byteBuffer[0] = ByteBuffer.wrap("Test0".getBytes(StandardCharsets.UTF_8));
        byteBuffer[1] = ByteBuffer.wrap("Test1".getBytes(StandardCharsets.UTF_8));
        sizeToMove = 2000;
        expectedParam = 10L;
        inputs.add(new TestInput(fi,file1,sizeToMove,0L,byteBuffer,expectedParam));

        /*
        Test 4 -> Test per scritture nulle (branch 531)
        */
        file0 = new File("tmp","file0.log");
        file1 = new File("tmp","file1.log");
        file0.deleteOnExit();
        file1.deleteOnExit();
        fi = new FileInfo(file0,"MasterKey".getBytes(StandardCharsets.UTF_8),FileInfo.V0);
        byteBuffer = new ByteBuffer[2];
        byteBuffer[0] = ByteBuffer.wrap("Test0".getBytes(StandardCharsets.UTF_8));
        byteBuffer[1] = ByteBuffer.wrap("Test1".getBytes(StandardCharsets.UTF_8));
        sizeToMove= 0;
        expectedParam = 10L;
        inputs.add(new TestInput(fi,file1,sizeToMove,0L,byteBuffer,expectedParam));

        /*
        Test 5 e 6 -> Test per file nullo o byteBuffer nullo
        */
        file0 = new File("tmp","file0.log");
        file0.deleteOnExit();
        fi = new FileInfo(file0,"MasterKey".getBytes(StandardCharsets.UTF_8),FileInfo.V0);
        byteBuffer = new ByteBuffer[2];
        byteBuffer[0] = ByteBuffer.wrap("Test0".getBytes(StandardCharsets.UTF_8));
        byteBuffer[1] = ByteBuffer.wrap("Test1".getBytes(StandardCharsets.UTF_8));
        sizeToMove= 0;
        expectedParam = 10L;
        inputs.add(new TestInput(fi,null,sizeToMove,0L,byteBuffer,expectedParam));
        inputs.add(new TestInput(fi,file0,sizeToMove,0L,null,expectedParam));

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
        // Create the directory if not present
        if (!Files.exists(Paths.get("tmp"))) {
            File tmpDir = new File("tmp");
            tmpDir.mkdir();
        }

        // Delete previous file tests if exist
        if (Files.exists(Paths.get("tmp", "file0.log"))) {
            File testFile = new File("tmp", "file0.log");
            testFile.delete();
        }

        if (Files.exists(Paths.get("tmp","file1.log"))) {
            File testFile = new File("tmp", "file1.log");
            testFile.delete();
        }
    }

    @AfterClass
    public static void cleanUpEnvironment() throws IOException {
        // Delete the directory
        if (Files.exists(Paths.get("tmp"))) {
            FileUtils.deleteDirectory(new File("tmp"));
        }
    }

    @Test
    public void moveToNewLocation_test() throws IOException {

        if(file == null || bytesToWrite == null)
            thrown.expect(NullPointerException.class);

        long numBytesWritten = fileInfo.write(bytesToWrite,position);
        Assert.assertEquals(expectedParam,numBytesWritten);

        fileInfo.moveToNewLocation(file, sizeToMove);

        if(sizeToMove>1034)
            sizeToMove = 1034;
        else if(sizeToMove < 0)
            sizeToMove = 0;

        Assert.assertEquals(sizeToMove,file.length());
    }

    private static class TestInput {
        FileInfo fileInfo;
        File file;
        long size;
        long position;
        ByteBuffer[] bytesToWrite;
        Object expectedParam;
        public TestInput(FileInfo fileInfo, File file, long size, long position, ByteBuffer[] bytesToWrite, Object expectedParam) {
            this.bytesToWrite = bytesToWrite;
            this.position = position;
            this.fileInfo = fileInfo;
            this.file = file;
            this.size = size;
            this.expectedParam = expectedParam;
        }
    }
}
