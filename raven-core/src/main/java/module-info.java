/**
 * Raven core: the plain Java TCP transport. Only the API packages are exported; the implementation lives in
 * {@code io.github.trimax.raven.core.internal} and its subpackages, which are not.
 *
 * <p>Message validation reads message fields reflectively, so a modular application must open the packages of its
 * messages to this module, e.g. {@code opens com.example.messages to io.github.trimax.raven.core;}.
 */
module io.github.trimax.raven.core {
    exports io.github.trimax.raven.core;
    exports io.github.trimax.raven.core.codec;
    exports io.github.trimax.raven.core.config;
    exports io.github.trimax.raven.core.exception;
    exports io.github.trimax.raven.core.handler;
    exports io.github.trimax.raven.core.interceptor;
    exports io.github.trimax.raven.core.type;
    exports io.github.trimax.raven.core.util;
    exports io.github.trimax.raven.core.util.interfaces;
    exports io.github.trimax.raven.core.util.retry;
    exports io.github.trimax.raven.core.validation;
    exports io.github.trimax.raven.core.validation.annotation;
    exports io.github.trimax.raven.core.validation.validator;

    requires org.apache.commons.io;
    requires org.slf4j;
    requires static lombok;
}
