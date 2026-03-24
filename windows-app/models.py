import json
import uuid
from dataclasses import dataclass, field
from typing import List, Optional

@dataclass
class Event:
    type: str
    target: str
    value: str = ""
    timestamp: int = 0
    optional: bool = False

    def to_dict(self):
        return {
            "type": self.type,
            "target": self.target,
            "value": self.value,
            "timestamp": self.timestamp,
            "optional": self.optional
        }

    @staticmethod
    def from_dict(d: dict):
        return Event(
            type=d.get("type", ""),
            target=d.get("target", ""),
            value=d.get("value", ""),
            timestamp=d.get("timestamp", 0),
            optional=d.get("optional", False)
        )

@dataclass
class ServiceData:
    id: str = field(default_factory=lambda: str(uuid.uuid4()))
    name: str = ""
    loginUrl: str = ""
    targetSuccessUrl: str = ""
    forceRedirectUrl: str = ""
    scriptJson: str = "[]"
    userAgent: str = ""
    requiresCaptcha: bool = False
    dynamicOcr: bool = False

    def to_dict(self):
        return {
            "id": self.id,
            "name": self.name,
            "loginUrl": self.loginUrl,
            "targetSuccessUrl": self.targetSuccessUrl,
            "forceRedirectUrl": self.forceRedirectUrl,
            "scriptJson": self.scriptJson,
            "userAgent": self.userAgent,
            "requiresCaptcha": self.requiresCaptcha,
            "dynamicOcr": self.dynamicOcr
        }

    @staticmethod
    def from_dict(d: dict):
        s = ServiceData(
            id=d.get("id", str(uuid.uuid4())),
            name=d.get("name", ""),
            loginUrl=d.get("loginUrl", ""),
            targetSuccessUrl=d.get("targetSuccessUrl", ""),
            forceRedirectUrl=d.get("forceRedirectUrl", ""),
            scriptJson=d.get("scriptJson", "[]"),
            userAgent=d.get("userAgent", ""),
            requiresCaptcha=d.get("requiresCaptcha", False),
            dynamicOcr=d.get("dynamicOcr", False)
        )
        return s
