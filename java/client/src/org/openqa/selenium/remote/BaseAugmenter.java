// Licensed to the Software Freedom Conservancy (SFC) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The SFC licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.openqa.selenium.remote;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.html5.AddApplicationCache;
import org.openqa.selenium.remote.html5.AddLocationContext;
import org.openqa.selenium.remote.html5.AddWebStorage;
import org.openqa.selenium.remote.mobile.AddNetworkConnection;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

import static org.openqa.selenium.remote.CapabilityType.ROTATABLE;
import static org.openqa.selenium.remote.CapabilityType.SUPPORTS_APPLICATION_CACHE;
import static org.openqa.selenium.remote.CapabilityType.SUPPORTS_LOCATION_CONTEXT;
import static org.openqa.selenium.remote.CapabilityType.SUPPORTS_NETWORK_CONNECTION;
import static org.openqa.selenium.remote.CapabilityType.SUPPORTS_WEB_STORAGE;

/**
 * Enhance the interfaces implemented by an instance of the
 * {@link org.openqa.selenium.remote.RemoteWebDriver} based on the returned
 * {@link org.openqa.selenium.Capabilities} of the driver.
 *
 * Note: this class is still experimental. Use at your own risk.
 */
public abstract class BaseAugmenter {
  private final Map<Predicate<Capabilities>, AugmenterProvider> driverAugmentors = new HashMap<>();
  private final Map<Predicate<Capabilities>, AugmenterProvider> elementAugmentors = new HashMap<>();

  public BaseAugmenter() {
    addDriverAugmentation(SUPPORTS_LOCATION_CONTEXT, new AddLocationContext());
    addDriverAugmentation(SUPPORTS_APPLICATION_CACHE, new AddApplicationCache());
    addDriverAugmentation(SUPPORTS_NETWORK_CONNECTION, new AddNetworkConnection());
    addDriverAugmentation(SUPPORTS_WEB_STORAGE, new AddWebStorage());
    addDriverAugmentation(ROTATABLE, new AddRotatable());

    StreamSupport.stream(ServiceLoader.load(AugmenterProvider.class).spliterator(), false)
      .forEach(provider -> {
        driverAugmentors.put(provider.isApplicable(), provider);
      });
  }

  /**
   * Add a mapping between a capability name and the implementation of the interface that name
   * represents for instances of {@link org.openqa.selenium.WebDriver}.
   *<p>
   * Note: This method is still experimental. Use at your own risk.
   *
   * @param capabilityName The name of the capability to model
   * @param handlerClass The provider of the interface and implementation
   */
  public void addDriverAugmentation(String capabilityName, AugmenterProvider handlerClass) {
    driverAugmentors.put(check(capabilityName), handlerClass);
  }

  public void addDriverAugmentation(Predicate<Capabilities> predicate, AugmenterProvider handlerClass) {
    Objects.requireNonNull(predicate, "Check to use must be set.");
    Objects.requireNonNull(handlerClass, "Handler class to use must be set.");
    driverAugmentors.put(predicate, handlerClass);
  }

  /**
   * Add a mapping between a capability name and the implementation of the interface that name
   * represents for instances of {@link org.openqa.selenium.WebElement}.
   * <p>
   * Note: This method is still experimental. Use at your own risk.
   *
   * @param capabilityName The name of the capability to model
   * @param handlerClass The provider of the interface and implementation
   */
  public void addElementAugmentation(String capabilityName, AugmenterProvider handlerClass) {
    elementAugmentors.put(check(capabilityName), handlerClass);
  }

  public void addElementAugmentation(Predicate<Capabilities> predicate, AugmenterProvider handlerClass) {
    Objects.requireNonNull(predicate, "Check to use must be set.");
    Objects.requireNonNull(handlerClass, "Handler class to use must be set.");
    elementAugmentors.put(predicate, handlerClass);
  }

  private Predicate<Capabilities> check(String capabilityName) {
    return caps -> {
      Objects.requireNonNull(capabilityName, "Capability name to check must be set.");

      Object value = caps.getCapability(capabilityName);
      if (value instanceof Boolean && !((Boolean) value)) {
        return false;
      }
      return value != null;
    };
  }

  /**
   * Enhance the interfaces implemented by this instance of WebDriver iff that instance is a
   * {@link org.openqa.selenium.remote.RemoteWebDriver}.
   *
   * The WebDriver that is returned may well be a dynamic proxy. You cannot rely on the concrete
   * implementing class to remain constant.
   *
   * @param driver The driver to enhance
   * @return A class implementing the described interfaces.
   */
  public WebDriver augment(WebDriver driver) {
    RemoteWebDriver remoteDriver = extractRemoteWebDriver(driver);
    if (remoteDriver == null) {
      return driver;
    }
    return create(remoteDriver, driverAugmentors, driver);
  }

  /**
   * Enhance the interfaces implemented by this instance of WebElement iff that instance is a
   * {@link org.openqa.selenium.remote.RemoteWebElement}.
   *
   * The WebElement that is returned may well be a dynamic proxy. You cannot rely on the concrete
   * implementing class to remain constant.
   *
   * @param element The driver to enhance.
   * @return A class implementing the described interfaces.
   */
  public WebElement augment(RemoteWebElement element) {
    // TODO(simon): We should really add a "SelfDescribing" interface for this
    RemoteWebDriver parent = (RemoteWebDriver) element.getWrappedDriver();
    if (parent == null) {
      return element;
    }

    return create(parent, elementAugmentors, element);
  }

  /**
   * Subclasses should perform the requested augmentation.
   *
   * @param <X>             typically a RemoteWebDriver or RemoteWebElement
   * @param augmentors      augumentors to augment the object
   * @param driver          RWD instance
   * @param objectToAugment object to augment
   * @return an augmented version of objectToAugment.
   */
  protected abstract <X> X create(RemoteWebDriver driver, Map<Predicate<Capabilities>, AugmenterProvider> augmentors,
      X objectToAugment);

  /**
   * Subclasses should extract the remote webdriver or return null if it can't extract it.
   *
   * @param driver WebDriver instance to extract
   * @return extracted RemoteWebDriver or null
   */
  protected abstract RemoteWebDriver extractRemoteWebDriver(WebDriver driver);
}
