package com.julesG10;

import com.julesG10.game.*;
import com.julesG10.game.map.*;
import com.julesG10.game.player.Player;
import com.julesG10.graphics.Model;
import com.julesG10.graphics.Shader;
import com.julesG10.graphics.Texture;
import com.julesG10.network.Client;
import com.julesG10.network.server.game.GameServer;
import com.julesG10.utils.*;
import com.julesG10.utils.Timer;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Main {

    private long window;
    public static Size size;
    private GLFWVidMode glfwVidMode;

    public static final String TITLE = "Open Battles";
    private World world;
    private Client gameClient;

    private Size userSize = new Size(-1, -1);
    private boolean saveWorld = false;
    private boolean showFps = false;
    private boolean showGrid = false;
    private boolean fullscreen = true;

    private boolean clientMode = true;
    private String clientAddress = "127.0.0.1";
    private int clientPort = 0;

    private boolean serverMode = false;
    private boolean publicServer = false;
    private int serverPort = 0;

    private boolean consoleMode = false;
    private boolean debugConsole = false;

    public void run() {
        Console.active = this.consoleMode;
        if(this.debugConsole)
        {
            Console.active = true;
        }

        if (!this.consoleMode) {
            if (this.init()) {
                this.loop();

                if (this.saveWorld) {
                    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss");
                    LocalDateTime now = LocalDateTime.now();
                    WorldLoader.generate(this.world,
                            AssetsManager.getWorldDirectory() + File.separator + dtf.format(now) + ".map");
                }

                glfwFreeCallbacks(window);
                glfwDestroyWindow(window);
            }

            glfwTerminate();
        } else {
            Console.log("Console Mode");
            if (this.serverMode) {
                Console.log("Starting server");
                GameServer server = new GameServer(this.publicServer, this.serverPort);
                server.start();
            } else if (this.clientMode) {
                Console.log("Init client");
                String host = "127.0.0.1";
                int port = 0;
                Scanner scanner = new Scanner(System.in);

                if (this.clientAddress != null) {
                    host = this.clientAddress;
                } else {
                    System.out.print("(hostname)>");
                    host = scanner.nextLine();
                }

                if (this.clientPort != 0) {
                    port = this.clientPort;
                } else {
                    System.out.print("(port)>");
                    port = (int) Float.parseFloat(scanner.nextLine());
                }

                Client client = new Client(host, port);
                Console.log("Trying connect client to " + host + ":" + port);

                if (client.connect(5000)) {
                    Console.log("Client connected");
                    while (client.client.isConnected()) {
                        System.out.print("(send) -> ");
                        String input = scanner.nextLine();
                        client.send(input);

                        String data = client.receive();
                        if (data == null) {
                            break;
                        }

                        System.out.println("(receive) -> " + data);
                    }
                    client.close();
                    Console.log("Client close");
                } else {
                    Console.log("Fail to connect the client");
                }
            }
            Console.log("Exit");
        }
    }

    private boolean init() {
        if (!glfwInit()) {
            return false;
        }

        long monitor = glfwGetPrimaryMonitor();
        this.glfwVidMode = glfwGetVideoMode(monitor);
        glfwWindowHint(GLFW_RED_BITS, this.glfwVidMode.redBits());
        glfwWindowHint(GLFW_GREEN_BITS, this.glfwVidMode.greenBits());
        glfwWindowHint(GLFW_BLUE_BITS, this.glfwVidMode.blueBits());
        glfwWindowHint(GLFW_REFRESH_RATE, this.glfwVidMode.refreshRate());

        size = new Size(this.glfwVidMode.width(), this.glfwVidMode.height());
        if (fullscreen) {
            window = glfwCreateWindow(size.width, size.height, TITLE, monitor, NULL);
            if (window == NULL) {
                return false;
            }
            glfwSetWindowMonitor(window, monitor, 0, 0, size.width, size.height, this.glfwVidMode.refreshRate());
        } else {
            if (this.userSize.width != -1 && this.userSize.height != -1) {
                size = this.userSize;
            }

            window = glfwCreateWindow(size.width, size.height, TITLE, NULL, NULL);
            if (window == NULL) {
                return false;
            }
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CURSOR_HIDDEN, GLFW_TRUE);
        glfwWindowHint(GLFW_DOUBLEBUFFER, GL_FALSE);

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

        glfwSetWindowPos(window, (this.glfwVidMode.width() - this.size.width) / 2,
                (this.glfwVidMode.height() - this.size.height) / 2);

        glfwMakeContextCurrent(window);
        glfwSwapInterval(0);
        // VerticalSync
        glfwSwapInterval(GLFW_FALSE);

        glfwShowWindow(window);

        AssetsManager.Init();
        return true;
    }

    private boolean initGame() {
        Block.size = new Size(200,200);
        Player.size = Block.size;

        this.world = new World();
        this.gameClient = new Client(this.clientAddress, this.clientPort);
        world.addPlayer(new Player());
        world.camera = new Camera(new Vector2(0, 0), Block.size.mult(10));

        // player textures
        String[] playerAnimations = { "left", "right", "top", "bottom" };
        for (int i = 0; i < playerAnimations.length; i++) {
            Texture[] t = AssetsManager.loadTextureDirectory("player" + File.separator + playerAnimations[i]).clone();
            world.players.get(0).textures.add(t);
        }

        if(world.players.get(0).textures.size() == 0 || world.players.get(0).textures.get(0).length == 0)
        {
            return false;
        }
        world.players.get(0).texture = world.players.get(0).textures.get(0)[0];

        // world setup

        List<Pair<BlockType,Texture[]>> blockTextures = new ArrayList<>();
        for (String strBlock : Block.blockNames)
        {
            Pair<BlockType,Texture[]> p = new Pair<>(
                    Block.getTypeFromName(strBlock),
                    AssetsManager.loadTextureDirectory("blocks" + File.separator +strBlock).clone()
            );

            if(p.getValue().length == 0)
            {
                return false;
            }
            blockTextures.add(p);
        }

        List<Chunk> chunkList = new ArrayList<>();
        Chunk chunk = new Chunk(new Vector2(0, 0));

        for (int x = 0; x < Chunk.size.width; x++) {
            for (int y = 0; y < Chunk.size.height; y++) {
                Block block = new Block();
                block.position = new Vector2(x * Block.size.width, y * Block.size.height);
                block.textures = blockTextures.get(0).getValue();
                chunk.blocks[x * Chunk.size.width + y] = block;
            }
        }

        chunkList.add(chunk);
        world.chunks = chunkList;

        return true;
    }

    private void loop() {
        GL.createCapabilities();
        glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

        glViewport(0, 0, size.width, size.height);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();

        //glOrtho(0, size.width,size.height, 0, 1, -1);
        glMatrixMode(GL_MODELVIEW);

        if (!glIsEnabled(GL_BLEND)) {
            glEnable(GL_BLEND);
        }
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        if (!glIsEnabled(GL_TEXTURE_2D)) {
            glEnable(GL_TEXTURE_2D);
        }

        if (!this.initGame()) {
            return;
        }

        Game game = new Game(this.window, this.world, this.gameClient);
        game.start();

        Timer timer = new Timer();

        float[] vertices = new float[]{
                -0.5f,  0.5f,  0,
                 0.5f,  0.5f,  0,
                 0.5f, -0.5f,  0,
                -0.5f, -0.5f,  0,
        };

        float[] texture = new float[]{
                0, 0,
                1, 0,
                1, 1,
                0, 1,
        };
        int[] indices = new int[]
                {
                        0, 1, 2,
                        2, 3, 0
                };

        Model model = new Model(vertices, texture, indices);
        Texture grass = new Texture(AssetsManager.assetsPath + File.separator + "player" + File.separator + "left" + File.separator + "sprite_2.png");

        String shaderDir = AssetsManager.assetsPath + File.separator + "shaders" + File.separator;
        Shader shader = new Shader(shaderDir+"vertex.glsl",shaderDir+"fragment.glsl");
        if(!shader.isValid())
        {

        }

        while (!glfwWindowShouldClose(window)) {
            glfwPollEvents();
            float deltatime = timer.restart();

            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);


            //grass.bind();
            shader.bind();
            model.render();


            //this.world.render();

            // Vector2 b =
            // game.getPositionItemCamera(game.mousePosition(),world.players.get(0).position,world.camera.position,Main.size.toVector2().div(2),Block.size);

            if (this.showGrid) {
                this.renderBlockGrid(game);
            }

            if (this.showFps) {
                System.out.print("\rFPS " + (1.0 / deltatime));
            }
            glfwSwapBuffers(window);

        }
    }

    public void renderBlockGrid(Game game) {
        World world = game.world;
        for (int x = 0; x <= Main.size.width; x += Block.size.width) {
            for (int y = 0; y <= Main.size.height; y += Block.size.height) {
                Vector2 b = CameraUtils.getPositionItemCamera(new Vector2(x, y), world.players.get(0).position,
                        world.camera.position, Main.size.toVector2().div(2), Block.size);
                glBegin(GL_LINE_LOOP);
                glVertex2f(b.x, b.y);
                glVertex2f(Block.size.width + b.x, b.y);
                glVertex2f(Block.size.width + b.x, Block.size.height + b.y);
                glVertex2f(b.x, Block.size.height + b.y);
                glEnd();
            }
        }
    }

    public void parseArgs(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("-")) {
                String tmp = arg;
                tmp = tmp.substring(1);
                tmp = tmp.toLowerCase();
                String[] data = tmp.split("=");

                if (data.length == 2) {
                    switch (data[0]) {
                        case "fps":
                            this.showFps = (data[1].equals("true") ? true : false);
                            break;
                        case "grid":
                            this.showGrid = (data[1].equals("true") ? true : false);
                            break;

                        // Bug Camera + Block size

                        case "fullscreen":
                            this.fullscreen = (data[1].equals("true") ? true : false);
                            break;
                        case "size":
                            String[] sizeData = data[1].split("x");
                            if (sizeData.length == 2) {
                                this.userSize = new Size((int) Float.parseFloat(sizeData[0]),
                                        (int) Float.parseFloat(sizeData[1]));

                            }
                            break;

                        case "debug":
                            this.debugConsole = (data[1].equals("true") ? true : false);
                            break;
                        case "window":
                            this.consoleMode = !(data[1].equals("true") ? true : false);
                            break;
                        case "save":
                            this.saveWorld = (data[1].equals("true") ? true : false);
                            break;
                        case "server":
                            this.serverMode = (data[1].equals("true") ? true : false);
                            ;
                            this.clientMode = !this.serverMode;
                            break;
                        case "client":
                            this.clientMode = (data[1].equals("true") ? true : false);
                            ;
                            this.serverMode = !this.clientMode;
                            break;
                        case "port":
                            this.clientPort = (int) Float.parseFloat(data[1]);
                            this.serverPort = (int) Float.parseFloat(data[1]);
                            break;
                        case "address":
                            this.clientAddress = data[1];
                            break;
                        case "public":
                            this.publicServer = (data[1].equals("true") ? true : false);
                            break;
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        Main main = new Main();
        main.parseArgs(args);
        // SERVER -window=false -server=true -public=false -port=8080
        // CLIENT -window=false -server=false -public=false -port=8080
        // -address=192.168.1.16
        // CLIENT WINDOW -server=false -public=false -port=8080 -address=192.168.1.16
        // -window=true -fullscreen=false -size=500x500
        main.run();
    }

    /*
     * glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> { if (key
     * == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
     * glfwSetWindowShouldClose(window, true); } });
     */
}
