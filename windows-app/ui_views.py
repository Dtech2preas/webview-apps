import sys
import os
import uuid
import json
from PyQt5.QtWidgets import (
    QApplication, QMainWindow, QLabel, QVBoxLayout, QWidget, QPushButton,
    QGridLayout, QFrame, QScrollArea, QHBoxLayout, QFileDialog, QInputDialog, QMessageBox, QDialog, QTextEdit
)
from PyQt5.QtCore import Qt, QTimer, QUrl, pyqtSignal
from PyQt5.QtGui import QFont, QColor
from PyQt5.QtWebEngineWidgets import QWebEngineView, QWebEnginePage

from models import ServiceData
from dtech_file_manager import DTechFileManager
from results_helper import ResultsHelper
from ad_window import AdUnlockWindow

# Cyberpunk Colors
BG_COLOR = "#000000"
PRIMARY_COLOR = "#00E5FF"
SECONDARY_COLOR = "#D500F9"
TEXT_COLOR = "#FFFFFF"

class CyberpunkButton(QPushButton):
    def __init__(self, text, bg=PRIMARY_COLOR, fg=BG_COLOR):
        super().__init__(text)
        self.setFont(QFont("JetBrains Mono", 12, QFont.Bold))
        self.setStyleSheet(f"""
            QPushButton {{
                background-color: {bg};
                color: {fg};
                border: 2px solid {bg};
                border-radius: 5px;
                padding: 10px;
            }}
            QPushButton:hover {{
                background-color: {fg};
                color: {bg};
                border: 2px solid {bg};
            }}
        """)

class CredentialManagerDialog(QDialog):
    def __init__(self, service_id, parent=None):
        super().__init__(parent)
        self.setWindowTitle(f"CREDENTIAL MANAGER (Service: {service_id})")
        self.setGeometry(200, 200, 400, 500)
        self.setStyleSheet(f"background-color: {BG_COLOR}; color: {PRIMARY_COLOR};")
        self.service_id = service_id

        layout = QVBoxLayout()
        self.setLayout(layout)

        self.text_edit = QTextEdit()
        self.text_edit.setPlaceholderText("Paste email:pass pairs here...")
        self.text_edit.setStyleSheet("background-color: #111111; color: white; font-family: Courier New;")
        layout.addWidget(self.text_edit)

        btn_save = CyberpunkButton("SAVE CREDENTIALS", bg="#69F0AE")
        btn_save.clicked.connect(self.save_creds)
        layout.addWidget(btn_save)

        self.load_creds()

    def cred_file_path(self):
        return os.path.join(os.getcwd(), f"creds_{self.service_id}.txt")

    def load_creds(self):
        path = self.cred_file_path()
        if os.path.exists(path):
            with open(path, "r", encoding="utf-8") as f:
                self.text_edit.setPlainText(f.read())

    def save_creds(self):
        path = self.cred_file_path()
        with open(path, "w", encoding="utf-8") as f:
            f.write(self.text_edit.toPlainText())
        self.accept()

    def get_first_cred(self):
        text = self.text_edit.toPlainText()
        lines = [l.strip() for l in text.split('\n') if l.strip() and ':' in l]
        if lines:
            parts = lines[0].split(':', 1)
            return parts[0], parts[1]
        return None, None

class OverlayWindow(QDialog):
    def __init__(self, parent=None):
        super().__init__(parent, Qt.WindowStaysOnTopHint | Qt.FramelessWindowHint)
        self.setStyleSheet(f"background-color: {BG_COLOR}; border: 2px solid {SECONDARY_COLOR};")
        self.setGeometry(100, 100, 300, 450)

        layout = QVBoxLayout()
        self.setLayout(layout)

        title = QLabel("DTECH OVERLAY")
        title.setFont(QFont("JetBrains Mono", 14, QFont.Bold))
        title.setStyleSheet(f"color: {SECONDARY_COLOR};")
        title.setAlignment(Qt.AlignCenter)
        layout.addWidget(title)

        self.console = QTextEdit()
        self.console.setReadOnly(True)
        self.console.setStyleSheet(f"background-color: #111111; color: {PRIMARY_COLOR}; font-family: JetBrains Mono;")
        layout.addWidget(self.console)

        self.btn_record = CyberpunkButton("RECORD", bg="#FF5252")
        layout.addWidget(self.btn_record)

        self.btn_run = CyberpunkButton("START/RUN", bg="#69F0AE")
        layout.addWidget(self.btn_run)

        self.btn_creds = CyberpunkButton("CREDENTIALS")
        layout.addWidget(self.btn_creds)

        self.btn_results = CyberpunkButton("SESSION RESULTS", bg=SECONDARY_COLOR)
        layout.addWidget(self.btn_results)

        # Drag implementation
        self.old_pos = self.pos()

    def log(self, text, color=PRIMARY_COLOR):
        self.console.append(f'<span style="color: {color};">{text}</span>')

    def mousePressEvent(self, event):
        self.old_pos = event.globalPos()

    def mouseMoveEvent(self, event):
        delta = event.globalPos() - self.old_pos
        self.move(self.x() + delta.x(), self.y() + delta.y())
        self.old_pos = event.globalPos()

class DTechWebEnginePage(QWebEnginePage):
    def __init__(self, parent=None, console_callback=None):
        super().__init__(parent)
        self.console_callback = console_callback

    def javaScriptConsoleMessage(self, level, msg, line, source):
        if self.console_callback:
            self.console_callback(level, msg, line, source)
        else:
            super().javaScriptConsoleMessage(level, msg, line, source)

class WebAutomationWindow(QMainWindow):
    def __init__(self, service: ServiceData):
        super().__init__()
        self.service = service
        self.setWindowTitle(f"DTECH Web Automation - {service.name}")
        self.setGeometry(200, 200, 800, 600)

        self.browser = QWebEngineView()

        # Override page to capture JS console logs correctly
        self.page = DTechWebEnginePage(self.browser, console_callback=self.handle_console_msg)
        self.browser.setPage(self.page)
        self.setCentralWidget(self.browser)

        if self.service.userAgent:
            self.page.profile().setHttpUserAgent(self.service.userAgent)

        self.browser.setUrl(QUrl(self.service.loginUrl))

        self.overlay = OverlayWindow(self)
        self.overlay.show()

        from automation_engine import WebAutomationEngine
        self.engine = WebAutomationEngine(self.page, self.overlay)

        self.overlay.btn_record.clicked.connect(self.start_record)
        self.overlay.btn_run.clicked.connect(self.start_run)
        self.overlay.btn_creds.clicked.connect(self.manage_credentials)
        self.overlay.btn_results.clicked.connect(self.show_results)

    def handle_console_msg(self, level, msg, line, source):
        if "DTECH_EVENT|" in msg:
            self.engine.parse_console_message(msg)
        elif "DTECH_RUN|" in msg:
            self.overlay.log(msg.replace("DTECH_RUN|", ""), "#D500F9")
            if "COMPLETE" in msg:
                self.process_run_completion()
        else:
            print(f"JS: {msg}")

    def start_record(self):
        self.overlay.log("Recording started...")
        self.engine.inject_recording_script()

    def manage_credentials(self):
        dialog = CredentialManagerDialog(self.service.id, self)
        dialog.exec_()

    def show_results(self):
        # Open results text file
        path = ResultsHelper.get_results_file_path()
        if os.path.exists(path):
            if sys.platform == "win32":
                os.startfile(path)
            else:
                os.system(f"xdg-open {path}")
        else:
            QMessageBox.information(self, "Results", "No results file found.")

    def start_run(self):
        self.overlay.log("Batch run started...")
        dialog = CredentialManagerDialog(self.service.id, self)
        email, password = dialog.get_first_cred()

        if not email or not password:
            QMessageBox.warning(self, "No Credentials", "Please add credentials first.")
            return

        self.current_email = email
        self.current_password = password

        script_to_run = self.service.scriptJson
        if not script_to_run or script_to_run == "[]":
            if self.engine.recorded_events:
                script_to_run = json.dumps([e for e in self.engine.recorded_events])
            else:
                self.overlay.log("No script recorded or imported.", "#FF5252")
                return

        self.engine.execute_script(script_to_run, email, password)

    def process_run_completion(self):
        from PyQt5.QtWidgets import QApplication
        QApplication.processEvents()
        QTimer.singleShot(2000, self.perform_ocr_validation)

    def perform_ocr_validation(self):
        from ocr_manager import OCRManager
        # Capture screenshot of webview
        pixmap = self.browser.grab()
        img_path = os.path.join(os.getcwd(), "temp_capture.png")
        pixmap.save(img_path)

        text = OCRManager.extract_text(img_path)

        if "dashboard" in text.lower() or "welcome" in text.lower():
            self.overlay.log("OCR Validation: SUCCESS", "#69F0AE")
            ResultsHelper.log_result("SUCCESS", self.service.name, f"{self.current_email}:{self.current_password}")
        else:
            self.overlay.log("OCR Validation: FAILED", "#FF5252")
            ResultsHelper.log_result("FAILED", self.service.name, f"{self.current_email}:{self.current_password}")

        if os.path.exists(img_path):
            os.remove(img_path)

    def closeEvent(self, event):
        # Save recorded script if any
        if self.engine.recorded_events and (not self.service.scriptJson or self.service.scriptJson == "[]"):
            self.service.scriptJson = json.dumps([e for e in self.engine.recorded_events])
        self.overlay.close()
        event.accept()

class DashboardWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("DTECH COMMAND CENTER")
        self.setGeometry(100, 100, 1024, 768)
        self.setStyleSheet(f"background-color: {BG_COLOR};")

        self.services = []

        main_widget = QWidget()
        main_layout = QVBoxLayout()
        main_widget.setLayout(main_layout)
        self.setCentralWidget(main_widget)

        # Header
        header_layout = QHBoxLayout()
        self.title_label = QLabel("DTECH STATUS: ONLINE")
        self.title_label.setFont(QFont("JetBrains Mono", 24, QFont.Bold))
        self.title_label.setStyleSheet(f"color: {PRIMARY_COLOR};")
        header_layout.addWidget(self.title_label)

        btn_import = CyberpunkButton("IMPORT .DTECH")
        btn_import.clicked.connect(self.initiate_import)
        header_layout.addWidget(btn_import, alignment=Qt.AlignRight)

        main_layout.addLayout(header_layout)

        # Grid Scroll Area
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setStyleSheet("border: none;")

        self.grid_widget = QWidget()
        self.grid_layout = QGridLayout()
        self.grid_widget.setLayout(self.grid_layout)
        scroll.setWidget(self.grid_widget)

        main_layout.addWidget(scroll)

        # Add a default service for recording new
        default_svc = ServiceData(name="New Service", loginUrl="https://example.com")
        self.services.append(default_svc)

        self.update_grid()

        # Pulse Effect
        self.pulse_timer = QTimer()
        self.pulse_timer.timeout.connect(self.pulse_header)
        self.pulse_timer.start(1000)
        self.pulse_state = True

    def pulse_header(self):
        if self.pulse_state:
            self.title_label.setStyleSheet(f"color: {SECONDARY_COLOR};")
        else:
            self.title_label.setStyleSheet(f"color: {PRIMARY_COLOR};")
        self.pulse_state = not self.pulse_state

    def initiate_import(self):
        options = QFileDialog.Options()
        file_path, _ = QFileDialog.getOpenFileName(self, "Import DTECH Service", "", "DTECH Files (*.dtech)", options=options)
        if file_path:
            self.pending_import_path = file_path
            # Theatrical Import Ad
            self.ad_window = AdUnlockWindow(self, self.finalize_import)
            self.ad_window.exec_()

    def finalize_import(self):
        if hasattr(self, 'pending_import_path'):
            service = DTechFileManager.import_service(self.pending_import_path)
            if service:
                service.id = str(uuid.uuid4()) # Clone with new ID
                if "(Imported)" not in service.name:
                    service.name += " (Imported)"
                self.services.append(service)
                self.update_grid()
                QMessageBox.information(self, "Success", f"Imported {service.name}")
            else:
                QMessageBox.critical(self, "Error", "Failed to parse .dtech file")

    def export_service(self, service):
        if not service.scriptJson or service.scriptJson == "[]":
            QMessageBox.warning(self, "Export Failed", "No script recorded to export.")
            return

        options = QFileDialog.Options()
        file_path, _ = QFileDialog.getSaveFileName(self, "Export DTECH Service", f"{service.name}.dtech", "DTECH Files (*.dtech)", options=options)

        if file_path:
            author, ok1 = QInputDialog.getText(self, "Export", "Author Name:")
            desc, ok2 = QInputDialog.getText(self, "Export", "Description:")
            if ok1 and ok2:
                DTechFileManager.export_service(service, file_path, author, desc)
                QMessageBox.information(self, "Success", "Service Exported Successfully!")

    def create_service_card(self, service, row, col):
        card = QFrame()
        card.setStyleSheet(f"background-color: #111111; border: 1px solid {PRIMARY_COLOR}; border-radius: 10px;")
        card_layout = QVBoxLayout()
        card.setLayout(card_layout)

        name_label = QLabel(service.name)
        name_label.setFont(QFont("JetBrains Mono", 16, QFont.Bold))
        name_label.setStyleSheet(f"color: {PRIMARY_COLOR}; border: none;")
        card_layout.addWidget(name_label)

        url_label = QLabel(service.loginUrl)
        url_label.setFont(QFont("JetBrains Mono", 10))
        url_label.setStyleSheet(f"color: #AAAAAA; border: none;")
        card_layout.addWidget(url_label)

        btn_layout = QHBoxLayout()

        btn_open = CyberpunkButton("OPEN")
        btn_open.clicked.connect(lambda: self.open_workspace(service))
        btn_layout.addWidget(btn_open)

        btn_export = CyberpunkButton("EXPORT", bg="#D500F9")
        btn_export.clicked.connect(lambda: self.export_service(service))
        btn_layout.addWidget(btn_export)

        card_layout.addLayout(btn_layout)
        self.grid_layout.addWidget(card, row, col)

    def update_grid(self):
        for i in reversed(range(self.grid_layout.count())):
            self.grid_layout.itemAt(i).widget().setParent(None)

        row, col = 0, 0
        for service in self.services:
            self.create_service_card(service, row, col)
            col += 1
            if col > 2:
                col = 0
                row += 1

    def open_workspace(self, service):
        # Store in list to prevent garbage collection
        if not hasattr(self, 'workspaces'):
            self.workspaces = []
        win = WebAutomationWindow(service)
        self.workspaces.append(win)
        win.show()

if __name__ == '__main__':
    app = QApplication(sys.argv)
    window = DashboardWindow()
    window.show()
    sys.exit(app.exec_())
