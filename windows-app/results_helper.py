import os

class ResultsHelper:
    FILE_NAME = "batch_results.txt"

    @staticmethod
    def get_results_file_path() -> str:
        # Save in the same directory as the script
        return os.path.join(os.getcwd(), ResultsHelper.FILE_NAME)

    @staticmethod
    def log_result(status: str, service_name: str, creds: str, label_value: str = ""):
        try:
            path = ResultsHelper.get_results_file_path()
            with open(path, "a", encoding="utf-8") as f:
                if label_value:
                    line = f"{status} | {service_name} | {creds} | {label_value} (powered by DTECH https://t.me/DTECHX24)\n"
                else:
                    line = f"{status} | {service_name} | {creds} (powered by DTECH https://t.me/DTECHX24)\n"
                f.write(line)
        except Exception as e:
            print(f"Error logging result: {e}")
