import os
import sys
import pytesseract
from PIL import Image

class OCRManager:
    @staticmethod
    def get_tesseract_cmd() -> str:
        # Check if we are running in a PyInstaller bundle
        if getattr(sys, 'frozen', False):
            # The application is frozen
            base_dir = sys._MEIPASS
        else:
            # The application is not frozen
            base_dir = os.path.dirname(os.path.abspath(__file__))

        tess_path = os.path.join(base_dir, "tesseract", "tesseract.exe")
        return tess_path

    @staticmethod
    def extract_text(image_path: str, lang: str = 'eng') -> str:
        try:
            cmd = OCRManager.get_tesseract_cmd()
            if os.path.exists(cmd):
                pytesseract.pytesseract.tesseract_cmd = cmd

                # Set tessdata prefix
                tessdata_dir = os.path.dirname(cmd)
                os.environ["TESSDATA_PREFIX"] = os.path.join(tessdata_dir, "tessdata")
            else:
                print(f"Warning: Bundled tesseract not found at {cmd}, falling back to system PATH")

            img = Image.open(image_path)
            text = pytesseract.image_to_string(img, lang=lang)
            return text.strip()
        except Exception as e:
            print(f"OCR Error: {e}")
            return ""
