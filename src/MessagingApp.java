import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

public class MessagingApp {
    private static final int PORT = 12345;
    private static final String SERVER_IP = "26.161.44.203"; // IP
    private static final Map<String, Socket> clients = new HashMap<>();
    private static boolean isServerRunning = false;
    private static PrintWriter out;
    private static boolean isCodeEntered = false;
    private static JTextArea messageHistoryArea;
    private static JLabel clientCountLabel;
    private static JFrame statusFrame;

    public static void main(String[] args) {
        JFrame frame = new JFrame("Мессенджер");
        frame.setSize(400, 500);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(null);

        JButton serverButton = new JButton("Запустить сервер");
        serverButton.setBounds(50, 30, 300, 30);
        frame.add(serverButton);

        serverButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!isServerRunning) {
                    new Thread(() -> startServer()).start();
                    isServerRunning = true;
                    showServerStatus();
                } else {
                    JOptionPane.showMessageDialog(frame, "Сервер уже запущен!");
                }
            }
        });

        JButton clientButton = new JButton("Войти");
        clientButton.setBounds(50, 80, 300, 30);
        frame.add(clientButton);

        clientButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showClientWindow();
            }
        });

        frame.setVisible(true);
    }

    private static void showServerStatus() {
        statusFrame = new JFrame("Статус сервера");
        statusFrame.setSize(300, 200);
        statusFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        statusFrame.setLayout(null);

        JLabel statusLabel = new JLabel("Статус: Запущен");
        statusLabel.setBounds(20, 20, 250, 30);
        statusFrame.add(statusLabel);

        JLabel ipLabel = new JLabel("IP-адрес: " + SERVER_IP);
        ipLabel.setBounds(20, 60, 250, 30);
        statusFrame.add(ipLabel);

        clientCountLabel = new JLabel("Количество подключенных пользователей: " + clients.size());
        clientCountLabel.setBounds(20, 100, 250, 30);
        statusFrame.add(clientCountLabel);

        JButton refreshButton = new JButton("Обновить");
        refreshButton.setBounds(80, 140, 140, 30);
        statusFrame.add(refreshButton);

        refreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                clientCountLabel.setText("Кол-во подключенных пользователей: " + clients.size());
            }
        });

        statusFrame.setVisible(true);
    }

    private static void startServer() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Сервер запущен.");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new ClientHandler(clientSocket).start();
                updateClientCount();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void updateClientCount() {
        SwingUtilities.invokeLater(() -> {
            clientCountLabel.setText("Количество подключенных пользователей: " + clients.size());
        });
    }

    private static void showClientWindow() {
        JFrame clientFrame = new JFrame("Клиент");
        clientFrame.setSize(400, 500);
        clientFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        clientFrame.setLayout(null);

        JLabel codeLabel = new JLabel("Введите 5-значный код:");
        codeLabel.setBounds(50, 20, 200, 30);
        clientFrame.add(codeLabel);

        JTextField codeField = new JTextField();
        codeField.setBounds(50, 60, 200, 30);
        clientFrame.add(codeField);

        JButton connectButton = new JButton("Подключиться");
        connectButton.setBounds(260, 60, 120, 30);
        clientFrame.add(connectButton);

        JLabel messageLabel = new JLabel("Введите сообщение:");
        messageLabel.setBounds(50, 100, 200, 30);
        clientFrame.add(messageLabel);

        JTextField messageField = new JTextField();
        messageField.setBounds(50, 130, 200, 30);
        clientFrame.add(messageField);

        JButton sendMessageButton = new JButton("Отправить сообщение");
        sendMessageButton.setBounds(260, 130, 120, 30);
        clientFrame.add(sendMessageButton);


        messageHistoryArea = new JTextArea();
        messageHistoryArea.setBounds(50, 180, 330, 250);
        messageHistoryArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(messageHistoryArea);
        scrollPane.setBounds(50, 180, 330, 250);
        clientFrame.add(scrollPane);

        connectButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String code = codeField.getText();
                if (!isCodeEntered && code.length() == 5) {
                    try {
                        Socket socket = new Socket(SERVER_IP, PORT);
                        out = new PrintWriter(socket.getOutputStream(), true);
                        out.println(code);
                        isCodeEntered = true;
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                } else if (isCodeEntered) {
                    JOptionPane.showMessageDialog(clientFrame, "Код уже был введен!");
                } else {
                    JOptionPane.showMessageDialog(clientFrame, "Код должен содержать 5 цифр!");
                }
            }
        });

        sendMessageButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String message = messageField.getText();
                if (isCodeEntered && !message.isEmpty()) {
                    sendMessage(message);
                    messageField.setText("");
                } else {
                    JOptionPane.showMessageDialog(clientFrame, "Сначала подключитесь и введите сообщение!");
                }
            }
        });

        clientFrame.setVisible(true);
    }

    private static void sendMessage(String message) {
        try {
            if (out != null) {
                out.println(message);
                messageHistoryArea.append("\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static class ClientHandler extends Thread {
        private Socket socket;
        private String userCode;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                userCode = in.readLine();

                synchronized (clients) {
                    clients.put(userCode, socket);
                }

                updateClientCount();

                String message;
                while ((message = in.readLine()) != null) {
                    broadcastMessage(message); //
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                synchronized (clients) {
                    clients.remove(userCode);
                }
                updateClientCount();
            }
        }

        private void broadcastMessage(String message) {
            synchronized (clients) {
                for (Socket clientSocket : clients.values()) {
                    try {
                        PrintWriter dataOut = new PrintWriter(clientSocket.getOutputStream(), true);
                        dataOut.println(message);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                MessagingApp.messageHistoryArea.append(userCode + ": " + message + "\n");
            }
        }
    }
}