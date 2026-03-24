import json
import base64
from models import ServiceData
from typing import Optional

class DTechFileManager:
    DTECH_SECURE_KEY_2024 = "DTECH_SECURE_KEY_2024"
    DTECH_SEPARATOR = "-" * 50

    @staticmethod
    def _xor_encrypt_decrypt(input_str: str, key: str) -> str:
        """XOR encrypts/decrypts the input string using the given key.
        Matches the Java byte-by-byte XOR logic.
        """
        input_bytes = input_str.encode('utf-8')
        key_bytes = key.encode('utf-8')
        result_bytes = bytearray()
        for i in range(len(input_bytes)):
            result_bytes.append(input_bytes[i] ^ key_bytes[i % len(key_bytes)])
        return result_bytes.decode('utf-8', errors='ignore')

    @staticmethod
    def _xor_encrypt_b64(input_str: str, key: str) -> str:
        input_bytes = input_str.encode('utf-8')
        key_bytes = key.encode('utf-8')
        result_bytes = bytearray()
        for i in range(len(input_bytes)):
            result_bytes.append(input_bytes[i] ^ key_bytes[i % len(key_bytes)])
        return base64.b64encode(result_bytes).decode('utf-8')

    @staticmethod
    def _xor_decrypt_b64(input_b64: str, key: str) -> str:
        input_bytes = base64.b64decode(input_b64)
        key_bytes = key.encode('utf-8')
        result_bytes = bytearray()
        for i in range(len(input_bytes)):
            result_bytes.append(input_bytes[i] ^ key_bytes[i % len(key_bytes)])
        return result_bytes.decode('utf-8', errors='ignore')

    @staticmethod
    def export_service(service: ServiceData, filepath: str, author_name: str, description: str):
        json_payload = json.dumps(service.to_dict())
        encrypted_payload = DTechFileManager._xor_encrypt_b64(json_payload, DTechFileManager.DTECH_SECURE_KEY_2024)

        metadata = f"DTECH AUTOMATION SCRIPT\n" \
                   f"Name: {service.name}\n" \
                   f"Target: {service.loginUrl}\n" \
                   f"Author: {author_name}\n" \
                   f"Description: {description}\n" \
                   f"{DTechFileManager.DTECH_SEPARATOR}\n"

        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(metadata + encrypted_payload)

    @staticmethod
    def import_service(filepath: str) -> Optional[ServiceData]:
        try:
            with open(filepath, 'r', encoding='utf-8') as f:
                content = f.read()

            payload = content
            if DTechFileManager.DTECH_SEPARATOR in content:
                parts = content.split(DTechFileManager.DTECH_SEPARATOR)
                if len(parts) > 1:
                    payload = parts[1].strip()

            if payload.startswith('{') or payload.startswith('['):
                # Legacy plain JSON format
                json_str = payload
            else:
                # Decrypt
                json_str = DTechFileManager._xor_decrypt_b64(payload, DTechFileManager.DTECH_SECURE_KEY_2024)

            data_dict = json.loads(json_str)
            return ServiceData.from_dict(data_dict)

        except Exception as e:
            print(f"Error importing service: {e}")
            return None
