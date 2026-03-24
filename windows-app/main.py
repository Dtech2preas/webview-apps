import sys
from PyQt5.QtWidgets import QApplication
from ui_views import DashboardWindow
from PyQt5.QtGui import QFont

def main():
    app = QApplication(sys.argv)

    # Force Monospace font (e.g., JetBrains Mono)
    font = QFont("Courier New", 10)
    app.setFont(font)

    window = DashboardWindow()
    window.show()
    sys.exit(app.exec_())

if __name__ == "__main__":
    main()
