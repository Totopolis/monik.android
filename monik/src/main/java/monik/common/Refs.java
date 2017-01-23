package monik.common;

public final class Refs {

    private Refs() {
    }

    public static final class Ref<T> {

        public T obj;

        public Ref() {
        }

        public Ref(T obj) {
            this.obj = obj;
        }
    }

    public static final class VolatileRef<T> {

        public volatile T obj;

        public VolatileRef() {
        }

        public VolatileRef(T obj) {
            this.obj = obj;
        }
    }
}
