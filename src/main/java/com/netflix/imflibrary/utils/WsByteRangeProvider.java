package com.netflix.imflibrary.utils;

import org.phoenixframework.channels.*;
import java.io.*;
import java.util.Base64;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class WsByteRangeProvider implements ResourceByteRangeProvider {
    private static final int EOF = -1;
    private static final int BUFFER_SIZE = 1024;
    private long fileSize;
    private WsFileLocator wsFileLocator;

    /**
     * Constructor for a WsByteRangeProvider
     */
    public WsByteRangeProvider(WsFileLocator wsFileLocator)
    {
        this.wsFileLocator = wsFileLocator;
        this.fileSize = this.wsFileLocator.length();
    }

    /**
     * A method that returns the size in bytes of the underlying resource, in this case a File
     * @return the size in bytes of the underlying resource, in this case a File
     */
    public long getResourceSize()
    {
        return this.fileSize;
    }

    /**
     * A method to obtain bytes in the inclusive range [start, endOfFile] as a file
     *
     * @param rangeStart zero indexed inclusive start offset; ranges from 0 through (resourceSize -1) both included
     * @param workingDirectory the working directory where the output file is placed
     * @return file containing desired byte range from rangeStart through end of file
     * @throws IOException - any I/O related error will be exposed through an IOException
     */
    public File getByteRange(long rangeStart, File workingDirectory) throws IOException
    {
        return this.getByteRange(rangeStart, this.fileSize - 1, workingDirectory);
    }

    /**
     * A method to obtain bytes in the inclusive range [start, end] as a file
     *
     * @param rangeStart zero indexed inclusive start offset; range from [0, (resourceSize -1)] inclusive
     * @param rangeEnd zero indexed inclusive end offset; range from [0, (resourceSize -1)] inclusive
     * @param workingDirectory the working directory where the output file is placed
     * @return file containing desired byte range
     * @throws IOException - any I/O related error will be exposed through an IOException
     */
    public File getByteRange(long rangeStart, long rangeEnd, File workingDirectory) throws IOException
    {
        System.out.println("get byte range");
        System.out.println(rangeStart + " -> " + rangeEnd);
        return null;
    }

    /**
     * This method provides a way to obtain a byte range from the resource in-memory. A limitation of this method is
     * that the total size of the byte range request is capped at 0x7fffffff (the maximum value possible for type int
     * in java)
     *
     * @param rangeStart zero indexed inclusive start offset; ranges from 0 through (resourceSize -1) both included
     * @param rangeEnd zero indexed inclusive end offset; ranges from 0 through (resourceSize -1) both included
     * @return byte[] containing desired byte range
     * @throws IOException - any I/O related error will be exposed through an IOException
     */
    public byte[] getByteRangeAsBytes(long rangeStart, long rangeEnd) throws IOException
    {
        Socket socket = new Socket(this.wsFileLocator.connect_url);
        socket.connect(this.wsFileLocator.token);
        ObjectMapper channel_mapper = new ObjectMapper();
        JsonNode channel_payload = channel_mapper.readTree("{}");
        Channel channel_ui_agent = new Channel("ui_agent:all", channel_payload, socket);
        channel_ui_agent.join();

        WsMessageCallback callback = new WsMessageCallback("ui_agent:all", "ls_response");
        socket.onMessage(callback);

        String agent = this.wsFileLocator.getAgent();
        String name = this.wsFileLocator.getAgentPath();

        ObjectMapper mapper = new ObjectMapper();
        String jsonString = String.format(
            "{\"body\": {\"agent\":\"%s\", \"path\": \"%s\", \"start\": %d, \"size\": %d}}",
            agent,
            name,
            rangeStart,
            rangeEnd - rangeStart + 1
        );

        JsonNode payload = mapper.readTree(jsonString);
        Envelope send_envelope = new Envelope("ui_agent:all", "get_file_content", payload, null, null);

        socket.push(send_envelope);

        try {
            while (!callback.received()) {
                Thread.sleep(10);
            }
            Envelope envelope = callback.getEnvelope();
            channel_ui_agent.leave();
            channel_ui_agent.close();
            socket.removeAllChannels();
            socket.disconnect();

            while (socket.isConnected()) {
                Thread.sleep(10);
            }

            socket.close();
            return Base64.getDecoder().decode(envelope.getPayload().get("data").asText());
        } catch (InterruptedException e) {
            System.out.println(e);
        }
        return new byte[0];
    }

    public InputStream getByteRangeAsStream(long rangeStart, long rangeEnd) throws IOException {
        return new ByteArrayInputStream(getByteRangeAsBytes(rangeStart, rangeEnd));
    }
}