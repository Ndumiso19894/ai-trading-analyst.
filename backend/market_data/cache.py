"""Small bounded in-process cache; source timestamp stays attached to cached payload."""
from collections import OrderedDict
from time import monotonic
import pandas as pd

class CandleCache:
    def __init__(self, ttl_seconds: int=60, max_items: int=64):
        self.ttl=ttl_seconds; self.max_items=max_items; self._items=OrderedDict()
    def get(self,key):
        item=self._items.get(key)
        if not item:return None
        expires,frame,metadata=item
        if monotonic()>expires:
            self._items.pop(key,None);return None
        self._items.move_to_end(key)
        return frame.copy(),metadata.copy()
    def put(self,key,frame,metadata):
        self._items[key]=(monotonic()+self.ttl,frame.copy(),metadata.copy())
        self._items.move_to_end(key)
        while len(self._items)>self.max_items:self._items.popitem(last=False)

cache=CandleCache()
