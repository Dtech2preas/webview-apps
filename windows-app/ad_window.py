from PyQt5.QtWidgets import QDialog, QVBoxLayout, QLabel, QPushButton, QTextEdit
from PyQt5.QtCore import QTimer, Qt, QUrl
from PyQt5.QtWebEngineWidgets import QWebEngineView

class AdUnlockWindow(QDialog):
    def __init__(self, parent=None, callback=None):
        super().__init__(parent)
        self.setWindowTitle("DTECH THEATRICAL IMPORT")
        self.setGeometry(300, 300, 600, 400)
        self.setStyleSheet("background-color: #000000; color: #00E5FF; font-family: JetBrains Mono;")

        self.callback = callback
        self.countdown = 15

        layout = QVBoxLayout()
        self.setLayout(layout)

        self.header = QLabel("UNLOCKING SECURE SCRIPT...")
        self.header.setAlignment(Qt.AlignCenter)
        layout.addWidget(self.header)

        self.browser = QWebEngineView()
        # Loading a sample ad URL from memory
        self.browser.setUrl(QUrl("https://otieu.com/4/10358600"))
        layout.addWidget(self.browser)

        self.btn_skip = QPushButton(f"WAIT {self.countdown}s")
        self.btn_skip.setEnabled(False)
        self.btn_skip.setStyleSheet("background-color: #333333; color: #777777; padding: 10px;")
        self.btn_skip.clicked.connect(self.accept_unlock)
        layout.addWidget(self.btn_skip)

        self.timer = QTimer()
        self.timer.timeout.connect(self.update_timer)
        self.timer.start(1000)

    def update_timer(self):
        self.countdown -= 1
        if self.countdown <= 0:
            self.timer.stop()
            self.btn_skip.setText("PROCEED")
            self.btn_skip.setEnabled(True)
            self.btn_skip.setStyleSheet("background-color: #00E5FF; color: #000000; padding: 10px; font-weight: bold;")
        else:
            self.btn_skip.setText(f"WAIT {self.countdown}s")

    def accept_unlock(self):
        if self.callback:
            self.callback()
        self.accept()
